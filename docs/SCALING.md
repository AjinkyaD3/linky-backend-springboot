# Scaling Linky

Design notes for taking Linky from one instance to a large deployment. The numbers marked
**measured** come from the load tests in [`loadtest/`](../loadtest/README.md); everything marked
**estimate** is back-of-envelope arithmetic, not a benchmark.

## Workload shape

A URL shortener is overwhelmingly **read-heavy**: a link is created once and clicked many times.
So the design optimises the redirect path (`GET /{code}`) and keeps everything else, including
analytics, off it.

## Redirect path

```
GET /{code}
  1. L1  Caffeine, in-process, 5 s TTL, single-flight loading     (no network)
  2. L2  Redis, 10 min TTL, plus 60 s negative cache for unknown codes
  3. DB  Postgres lookup by unique short_code index
  -> checks: expired / inactive / private owner / password / one-time (atomic DB claim)
  -> XADD click event to Redis Stream, 302
```

| Concern | Decision |
|---|---|
| Hot links | L1 absorbs them with zero network hops. **Measured:** one DB query served ~123,000 redirects. |
| Cache stampede | Caffeine `get(key, loader)` is single-flight, so concurrent misses on one code cause one load (unit-tested with 16 threads). |
| Unknown-code floods | Negative caching in L1 and L2, so scanning random codes cannot turn into a DB query per request. |
| Redis outage | Reads and writes are wrapped; the redirect falls back to Postgres. Redis commands time out after 500 ms, so a hung Redis cannot hold request threads. |
| Invalidation | Writes call `invalidate()`, clearing L1 and L2 on that instance. Other instances serve their L1 copy for at most 5 s. Acceptable for redirects; a delete is visible everywhere within seconds. |
| One-time links | Never trust a cached flag alone: the redirect claims the link with `UPDATE ... WHERE is_active = true`, so exactly one concurrent caller wins. |

## Click analytics pipeline

Writing a row per click on the request path ties redirect latency and throughput to the database.
Instead:

```
request thread:  capture headers, hash IP, XADD   (microseconds, no DB)
consumer group:  XREADGROUP 500 events -> parse UA / geo -> one JDBC batch insert
                 -> one counter UPDATE per link per batch -> XACK + XDEL
```

- **Scales horizontally.** Every instance joins the `click-writers` consumer group; Redis gives each
  event to exactly one member. **Measured:** with two instances under load the work was split and
  totals reconciled.
- **At-least-once delivery.** An entry is acknowledged only after its batch commits. A crashed
  consumer's entries are reclaimed by another instance after 60 s; an entry that fails 5 times is
  dead-lettered (counted and logged) so it can't loop forever.
- **Absorbs outages.** **Measured:** with every consumer stopped, 2,000 clicks buffered in the stream
  while redirects kept working, then drained into Postgres once a consumer returned.
- **Bounded memory.** The stream is trimmed to ~200k entries; if Redis is unreachable the event is
  dropped and `linky.clicks.dropped` increments. Analytics are best-effort by design; redirects are not.
- **Trade-off.** Click counts and the analytics dashboard are eventually consistent (typically well
  under a second behind). Duplicates are possible after a crash between commit and ack.

## Capacity estimates (assumptions stated)

Assume 100 M links and 100 M redirects/day.

- Average redirect rate: 100,000,000 / 86,400 ~ **1,160 req/s**; assume 3x peak ~ **3,500 req/s**.
- **Measured** on a developer laptop (single instance, 512 MB heap, Postgres and Redis on the same
  machine, load generator on the same machine): **~9,400 req/s sustained**, p99 server-side ~17 ms.
  So the redirect tier is not the bottleneck at this scale; run 2+ instances for availability, not capacity.
- `urls` row ~500 B: 100 M x 500 B ~ **50 GB**, plus ~6 GB for the short-code index. Fits one
  Postgres primary with a read replica.
- `url_clicks` row ~150 B: 100 M/day x 150 B ~ **15 GB/day**. This is the table that grows without
  bound: partition by month, keep raw rows for a retention window, and keep per-day rollups.
- Short-code space: 62^7 ~ **3.5 trillion** codes. With 100 M links, a random code collides with an
  existing one with probability ~ 3e-5 per attempt; the generator checks and retries.

## What to do next as load grows

| Pressure | Step |
|---|---|
| DB connections | Pool of 5 per instance (configurable `DB_POOL_SIZE`); put PgBouncer in front before adding many instances. |
| Redirect latency from far away | CDN / edge cache for 301/302 of public, non-one-time links. |
| `url_clicks` size and dashboard speed | Monthly partitions, daily rollup table, then move analytics to a column store (ClickHouse). |
| Stream throughput | Raise batch size; more consumer instances; or Kafka if you need replay and multiple independent consumers. |
| Read load on Postgres | Read replica for dashboard queries; redirects already rarely touch the DB. |
| Multi-region | Regional Redis and read replicas; writes to one primary; code generation is stateless so no coordination is needed. |

## Known limits (honest list)

- Benchmarks are single-machine and the load generator shares the CPU, so absolute numbers are a floor,
  not a ceiling, and are not a substitute for a test on production-like hardware.
- L1 invalidation is per-instance (seconds of staleness elsewhere); broadcast invalidation over Redis
  pub/sub would close that gap.
- Tests run on H2; production runs on Postgres. Testcontainers would close that gap.
