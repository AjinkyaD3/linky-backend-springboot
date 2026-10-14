# Load testing the redirect path

Tool: [autocannon](https://github.com/mcollina/autocannon) (`npx`, no install).

```bash
# 1. start the backend with a quiet config (SQL logging distorts results)
java -Xmx512m -jar target/linky-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=local --spring.jpa.show-sql=false \
  --logging.level.org.hibernate.SQL=WARN --logging.level.root=WARN

# 2. run the benchmark (creates a link, warms up, measures, then verifies no clicks were lost)
./loadtest/redirect.sh http://localhost:8080 64 20
```

## Results

Machine: 16-logical-core Windows laptop; Postgres 18, Redis 5 and the load generator all on the same
box; backend JVM capped at 512 MB. Redirects are 302s and are not followed.

| | Before | After |
|---|---|---|
| Hot path | Redis GET **and** a Postgres lookup on every redirect; click written on an `@Async` thread | L1/L2 cache, **no DB on a cache hit**; click is one Redis `XADD` |
| Throughput (64 connections, 20 s) | ~5,700 req/s | **~9,400 req/s** (peak ~14,700) |
| Clicks recorded | **Almost all lost**: ~124,000 `request object has been recycled` errors for ~113,000 requests | **100%**: 246,166 published = 246,166 persisted, 0 dropped, 0 dead-lettered |
| DB queries during the run | one+ per redirect | **2** (cache loads) for ~246,000 redirects |
| Server-side latency (Micrometer histogram, all 246k requests) | not measured | p50 <= 1 ms, p95 <= 5.6 ms, p99 <= 16.8 ms, p99.9 <= 89.5 ms |

Notes:

- The "before" bug was real: the old code passed the servlet request to another thread after Tomcat
  had recycled it, so analytics silently failed under load. The new design captures everything on
  the request thread.
- autocannon's own latency column is not quoted: with the client and server sharing one machine it
  is dominated by client-side queueing. Server-side percentiles come from Linky's own
  `http_server_requests_seconds` histogram (`/actuator/prometheus`).
- These are single-machine numbers. See [docs/SCALING.md](../docs/SCALING.md) for what they do and
  don't tell you.
