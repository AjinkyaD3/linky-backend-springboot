# Linky backend

Spring Boot API **and redirect engine** for [Linky](https://github.com/AjinkyaD3/linky), a URL shortener with smart routing,
link-in-bio pages, QR codes, analytics and signed webhooks.

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?logo=redis&logoColor=white)
[![CI](https://github.com/AjinkyaD3/linky-backend-springboot/actions/workflows/ci.yml/badge.svg)](https://github.com/AjinkyaD3/linky-backend-springboot/actions/workflows/ci.yml)

## What is in here

- `GET /{code}`: the redirect, served from a two-level cache (Caffeine, then Redis, then Postgres) with negative caching and single-flight loading
- A click pipeline on **Redis Streams** (consumer group, batched JDBC inserts, reclaim of stale entries, dead-lettering)
- Links: random codes, aliases, editable destination, expiry, device/OS/country routing, password / one-time / private links, bulk create, QR codes
- Link-in-bio pages, analytics with country and city (MaxMind GeoLite2 or DB-IP), signed **webhooks** (transactional outbox, retries, SSRF guard)
- JWT auth with rotating refresh tokens, API keys, Redis rate limiting, Flyway migrations, Prometheus metrics, structured logs

## Run it

Requirements: JDK 21+, Docker (for Postgres and Redis).

```bash
docker compose up -d                                         # Postgres 16 + Redis 7
./mvnw spring-boot:run -Dspring-boot.run.profiles=local      # http://localhost:8080
```

- Swagger UI: http://localhost:8080/swagger-ui.html (local profile only)
- Health: `/actuator/health` · version: `/actuator/info`
- With the frontend on port 4300: `FRONTEND_URL=http://localhost:4300 CORS_ALLOWED_ORIGINS=http://localhost:4300`

Profiles: `local` (own Postgres and Redis, SQL logging, Swagger, relaxed webhook checks), `prod` (everything from environment variables),
`json` (structured JSON logs), `test`. Copy `.env.example` for the full list of settings.

## Configuration

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASS`, `DB_POOL_SIZE` | PostgreSQL connection |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_USERNAME`, `REDIS_PASSWORD`, `REDIS_KEY_PREFIX` | Redis (needs Streams and Lua). Use the prefix if environments share an instance |
| `JWT_SECRET` | token signing secret |
| `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS`, `COOKIE_SECURE` | frontend origin(s) and cookie flag |
| `PUBLIC_BASE_URL` | what short links and QR codes use |
| `TRUSTED_PROXY_HOPS`, `IP_HASH_SALT` | client-IP resolution and stored IP hashes |
| `METRICS_TOKEN` | required `X-Metrics-Token` for `/actuator/prometheus` |
| `GEOIP_DB_PATH` | MaxMind-format city database for country/city analytics |
| `CLICK_CONSUMER_ENABLED`, `WEBHOOK_DISPATCHER_ENABLED` | run background workers on this instance |
| `WEBHOOK_RETRY_SECONDS`, `WEBHOOKS_ALLOW_INSECURE` | retry delays; local-development-only relaxation of webhook URL checks |
| `RESEND_API_KEY`, `MAIL_FROM` | password-reset email |

Never commit `.env`, `application-dev.yml` or credentials; they are git-ignored.

## Testing

```bash
./mvnw verify                                      # 115 tests + JaCoCo report in target/site/jacoco
bash scripts/smoke-test.sh http://localhost:8080   # end-to-end checks against a running backend
bash loadtest/redirect.sh http://localhost:8080 64 20
```

Unit and MockMvc tests run on in-memory H2 with Redis mocked (about 73% line coverage, controllers about 94%). The smoke script covers the paths
those tests cannot: real Postgres and Redis, the one-time-link race, the click pipeline, and webhooks. CI runs it against a freshly booted jar.

## Documentation

- [Scaling notes and capacity maths](docs/SCALING.md)
- [Webhooks: events, signatures, retries, security](docs/WEBHOOKS.md)
- [Geo analytics setup and licensing](docs/GEOIP.md)
- [Load-test method and results](loadtest/README.md)
- API reference: Swagger UI in the local profile

## Layout

```
src/main/java/com/ajinkya/linky/
  config/      security, JWT and API-key filters, rate limiter, request id, Redis, GeoIP, OpenAPI
  controller/  REST endpoints and the redirect
  service/     business logic, LinkCache, click publisher/consumer, webhooks
  entity/  repository/  dto/  util/  exception/
src/main/resources/db/migration/       Flyway migrations (V1 baseline ... V4 webhooks)
scripts/   loadtest/   observability/   docs/
```

## Known limitations

Paginated lists, SQL-aggregated analytics, object storage for avatars, encrypted webhook secrets, and URL reputation checks are not done yet. See the root README for the full list and roadmap.
