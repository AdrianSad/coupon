# Coupons service

REST service for issuing and redeeming discount coupons.

## What it does

- `POST /api/v1/coupons` — create a coupon with a unique code (case-insensitive),
  a max-uses cap and a target country.
- `POST /api/v1/coupons/{code}/redeem` — redeem the coupon for a user; the
  request's IP is geolocated and matched against the coupon's country.

Business rules enforced:

- coupon codes are case-insensitive (`WIOSNA` and `wiosna` are the same)
- redemptions are limited by `maxUses`, first-come-first-served
- redemptions are restricted to the coupon's country (IP geolocation)
- a given user can only redeem a given coupon once
- domain failures are returned as RFC 7807 `application/problem+json` with a
  stable `errorCode` (e.g. `coupon-exhausted`, `country-not-allowed`)

## Quick start

```bash
docker compose up --build
```

Then:

```bash
# create
curl -X POST http://localhost:8080/api/v1/coupons \
  -H 'Content-Type: application/json' \
  -d '{"code":"WIOSNA","maxUses":100,"country":"PL"}'

# redeem (Idempotency-Key is optional but recommended)
curl -X POST http://localhost:8080/api/v1/coupons/wiosna/redeem \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 7c1c1f77-a2c3-4b6a-9c4e-5b2bfb1b6c8b' \
  -H 'X-Forwarded-For: 8.8.8.8' \
  -d '{"userId":"user-1"}'
```

OpenAPI: <http://localhost:8080/swagger-ui.html>
Health:  <http://localhost:8080/actuator/health>
Metrics: <http://localhost:8080/actuator/prometheus>

## Running the backend from your IDE while Mongo runs in Docker

When the replica set is initialised inside the `mongo` container it advertises
itself to clients as `mongo:27017`. From inside the Docker network (the `app`
service) that hostname resolves; from your host machine it does not, and the
driver fails with `UnknownHostException: No such host is known (mongo)`. Two
ways to work around it:

1. **All-in-Docker (default)** - `docker compose up --build` runs the app
   alongside Mongo and Redis on the same network, no host-side changes needed.

2. **Host-side dev (Mongo in Docker, app from IDE / `mvn spring-boot:run`)** -
   teach your host that `mongo` means `127.0.0.1`. Run this once from an
   Administrator PowerShell:

   ```powershell
   Add-Content -Path $env:WINDIR\System32\drivers\etc\hosts -Value "`n127.0.0.1`tmongo"
   ```

   On macOS / Linux: `echo "127.0.0.1 mongo" | sudo tee -a /etc/hosts`. After
   that the same `mongodb://localhost:27017/coupons?replicaSet=rs0` URI works
   from both inside and outside the Docker network.

   `directConnection=true` is **not** an alternative - it disables the
   replica-set protocol and breaks multi-document transactions.

## Architecture

Hexagonal (ports & adapters):

```
domain         -> aggregates (Coupon), VOs, ports, exceptions          (no Spring, no Mongo)
application    -> use cases (CreateCoupon, RedeemCoupon)               (transactional boundary)
infrastructure
  persistence/mongo -> MongoDB adapters + indexes + tx manager
  geo               -> ip-api.com client + Caffeine cache + Resilience4j
  ratelimit         -> Bucket4j + Redis (Lettuce) ProxyManager
  web               -> Controller, DTOs, ProblemDetail handler,
                       Idempotency / RateLimit / RequestContext filters
  config            -> typed properties, OpenAPI, RestClient, Clock
```

Why hexagonal: business invariants live on the `Coupon` aggregate and are
exercised by fast unit tests independently of Spring or Mongo. External
providers (IP geolocation, rate-limit backend, even MongoDB) are pluggable
behind ports.

## How redemption stays correct under concurrency

`RedeemCouponUseCase` is `@Transactional` (Mongo multi-document transaction,
hence the replica-set requirement). Inside the transaction:

1. **Atomic conditional `findAndModify`** on the coupon, matching only when
   `currentUses < maxUses`. The increment and the cap check happen in a
   single round-trip — no two pods can ever observe "free capacity" and
   both succeed past the limit.
2. **Insert** into `redemptions` with a unique compound index on
   `(couponCode, userId)`. A second redemption from the same user fails the
   index, raises `CouponAlreadyRedeemedException`, and the transaction rolls
   back the `$inc` so the freed slot is returned to the pool.

The atomic step is the central guarantee; the Testcontainers concurrency test
fires 200 parallel redemptions at a coupon with `maxUses=50` and asserts
exactly 50 succeed.

## Cross-cutting concerns

| Concern             | Mechanism                                                                                     |
| ------------------- | --------------------------------------------------------------------------------------------- |
| Idempotency         | `Idempotency-Key` header, MongoDB-backed store with TTL index. Replay returns the same body.  |
| Distributed rate limit | Bucket4j + Redis (Lettuce) ProxyManager, keyed per IP. Returns 429 with `Retry-After`.     |
| IP geolocation      | `ip-api.com` REST client, Caffeine cache (10 min), Resilience4j retry + circuit breaker.       |
| Tracing             | Per-request UUID in MDC (`requestId`, `clientIp`, `couponCode`, `userId`); reflected in headers.|
| Errors              | RFC 7807 `application/problem+json` with stable `errorCode` strings.                          |
| Observability       | Spring Boot Actuator: `/actuator/health` (readiness/liveness), `/actuator/prometheus`.        |

## Scaling notes

- The service is fully stateless — scale by adding pods behind an Ingress.
- All shared state lives in MongoDB (data) and Redis (rate-limit buckets).
  No sticky sessions are required.
- Hot path for redemption is one indexed `findAndModify` plus one
  unique-indexed insert — both `O(log n)`.
- Idempotency-Key + transactional rollback make retries safe under network
  partitions.
- The rate-limit budget is shared across pods because Bucket4j stores buckets
  in Redis.
- `auto-index-creation` is disabled in production; indexes are created from
  one observable startup hook (logged) so an ops migration job can take over
  for collections that are too hot to index online.

Things deliberately not built (would be the next step):

- multi-tenancy / authentication / API keys
- read replicas + a separate read model for coupon analytics
- outbox -> Kafka for `CouponRedeemed` events
- per-user rate limits in addition to per-IP

## Running tests

```bash
mvn verify
```

Tests:

- domain unit tests (no Spring)
- use-case unit tests with mocked ports
- `@WebMvcTest` for the controller, exception handler, idempotency and
  rate-limit filters
- end-to-end `@SpringBootTest` with Testcontainers (MongoDB replica-set,
  Redis) and WireMock simulating `ip-api.com`. Includes a concurrency stress
  test that proves `maxUses` is never exceeded.

## Configuration

See `src/main/resources/application.yml`. Key knobs:

- `coupons.rate-limit.redeem.*` — bucket capacity / refill
- `coupons.idempotency.ttl-hours` — how long replay is honored
- `coupons.geolocation.*` — base URL, cache TTL, optional fallback country
- `resilience4j.retry.instances.ipApi` / `circuitbreaker.instances.ipApi`
