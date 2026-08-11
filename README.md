# Rate Limiter Service

A distributed, Redis-backed API rate limiter built with Spring Boot. It implements the **token bucket** algorithm, enforced **atomically** via a Lua script so multiple app instances can safely share one limit per client.

- Per-endpoint limits via a `@RateLimit(capacity, window)` annotation
- Client keying by `X-User-Id` header, falling back to IP address
- Standard `429 Too Many Requests` responses with `Retry-After` / `X-RateLimit-*` headers
- Distributed state in Redis — multiple app instances enforce the *same* limit for the same client
- Structured logging + a Micrometer counter for every throttled request

---

## Architecture

```
Client
  │  POST /demo/login  (X-User-Id: alice)
  ▼
┌─────────────────────────┐
│  RateLimitInterceptor    │  preHandle()
│  (HandlerInterceptor)    │  - reads @RateLimit off the handler method/class
└───────────┬──────────────┘  - resolves bucket key: user/ip + method + URI
            │
            ▼
┌─────────────────────────┐
│  RateLimiterService      │  picks annotation-supplied (capacity, refillRate)
│                          │  or falls back to application.yml defaults
└───────────┬──────────────┘
            │
            ▼
┌─────────────────────────┐
│ RedisRateLimiterRepository│  EVALs token_bucket.lua with
│                          │  KEYS[1]=bucket key, ARGV=[capacity, refillRate, ttl]
└───────────┬──────────────┘
            │
            ▼
┌─────────────────────────┐
│         Redis            │  HGET/HSET bucket state (tokens, lastRefill)
│  (shared across all app  │  atomically inside the Lua script — no
│   instances)              │  network round trip between check and decrement
└───────────┬──────────────┘
            │
            ▼
      {allowed, remainingTokens, retryAfterSeconds, resetSeconds}
            │
   ┌────────┴────────┐
   ▼                 ▼
 allowed           denied
   │                 │
   ▼                 ▼
request proceeds   RateLimitExceededException
+ X-RateLimit-*       │
  headers set          ▼
                 GlobalExceptionHandler
                 → 429 + Retry-After +
                   X-RateLimit-Reset + JSON body
```

Key classes, in call order:

| Class | Responsibility |
|---|---|
| `RateLimitInterceptor` | Entry point. Reads `@RateLimit` off the handler, resolves the client key, asks for a decision, sets response headers or throws. |
| `RateLimitKeyResolver` logic (in the interceptor) | Builds a key from `X-User-Id` (or IP) + HTTP method + URI, so `/login` and `/search` get independent buckets per client. |
| `RateLimiterService` | Chooses per-annotation limits (`capacity` / `capacity ÷ window`) or the `application.yml` defaults, then delegates to the repository. |
| `RedisRateLimiterRepository` | Executes `token_bucket.lua` via `StringRedisTemplate` and maps the raw Lua result into a `RateLimitDecision`. |
| `token_bucket.lua` | The actual token bucket math, run atomically inside Redis. |
| `GlobalExceptionHandler` | Catches `RateLimitExceededException` centrally and formats every 429 response the same way. |

---

## Why token bucket (over fixed window / sliding log)

**Fixed window counter** (e.g. "max 100 requests per calendar minute") is simple but has a well-known edge-case: a client can send 100 requests in the last second of one window and another 100 in the first second of the next, i.e. 200 requests in ~1 second, twice the intended rate. Windows reset dumbly by clock boundary, not by client behavior.

**Sliding window log** (storing a timestamp per request) fixes the boundary problem, but the storage cost is *O(requests per window)* per client — for a busy client with a large window that's a lot of memory/Redis keys per bucket, and computing "how many requests in the last N seconds" means scanning/trimming that list on every request.

**Token bucket** gives:
- **Smooth, not bursty-at-boundaries, enforcement** — tokens refill continuously (`refillRate` tokens/sec), not in a step function at window edges.
- **O(1) storage per client** — just two fields (`tokens`, `lastRefill`) in a Redis hash, regardless of window size or request volume. See `token_bucket.lua`.
- **Natural burst allowance** — a client that's been idle can burst up to `capacity` immediately, then is throttled to the steady-state `refillRate`. This matches how most real APIs actually want to behave (allow bursts, cap sustained rate).
- **Cheap "when can I retry" math** — because state is just `(tokens, lastRefill)`, computing `retryAfterSeconds` / `resetSeconds` is a closed-form calculation (`(1 - tokens) / refillRate`), not a scan.

The tradeoff: token bucket doesn't give you an exact "requests in the last calendar window" number the way a sliding log does — but for rate *limiting* (as opposed to analytics), that precision isn't needed, and the O(1) storage/compute win matters far more at scale.

---

## The race condition — and why the Lua script has to be atomic

A naive implementation does this in the application layer:

```
tokens = GET bucket:key         -- read
if tokens >= 1:
    tokens -= 1                 -- decide + mutate
    SET bucket:key tokens        -- write
```

Between the `GET` and the `SET`, two concurrent requests hitting the same key (same user, same endpoint, from two different app instances or even two threads on one instance) can both read `tokens = 1`, both decide "allowed", and both decrement — resulting in `tokens = -1` and **two requests admitted when only one token existed**. This is a classic check-then-act / TOCTOU race, and it gets worse under load exactly when the limiter matters most.

The fix (`token_bucket.lua`) moves the *entire* read-refill-check-decrement-write sequence into a single Lua script executed via Redis's `EVAL`. Redis executes Lua scripts **single-threaded and atomically** — no other command (including another invocation of the same script) can interleave partway through. So the whole "look at current tokens, refill for elapsed time, check `>= 1`, decrement, persist" operation is indivisible from every other client's point of view, regardless of how many app instances are calling it concurrently. This is also *why* the state had to move to Redis in the first place (Phase 3) rather than staying in a per-instance `ConcurrentHashMap`: an in-memory map only gives you atomicity within one JVM, not across the fleet.

```6:44:src/main/resources/token_bucket.lua
local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refillRate = tonumber(ARGV[2])
local ttl = tonumber(ARGV[3])

local time = redis.call("TIME")
local nowMs = tonumber(time[1]) * 1000 + tonumber(time[2]) / 1000

local bucket = redis.call("HMGET", key, "tokens", "lastRefill")
local tokens = tonumber(bucket[1])
local lastRefill = tonumber(bucket[2])
...
redis.call("HSET", key, "tokens", tokens, "lastRefill", nowMs)
redis.call("EXPIRE", key, ttl)

return {allowed, math.floor(tokens), retryAfter, resetSeconds}
```

Two more details worth noting:
- `redis.call("TIME")` is used instead of a timestamp passed in from the app, so the "elapsed time" calculation is based on Redis's clock, not the (possibly skewed) clock of whichever app instance happens to serve the request.
- `EXPIRE key ttl` (TTL derived from `capacity / refillRate`) prevents idle buckets from living in Redis forever — a bucket that hasn't been touched for long enough to have fully refilled anyway gets cleaned up automatically.

---

## API

### `@RateLimit` annotation

```java
@RateLimit(capacity = 5, window = 60) // 5 requests per 60s → refillRate = capacity/window
@PostMapping("/login")
public Map<String, Object> login() { ... }
```

Put it on a method (checked first) or a class (fallback default for all its endpoints). If absent, the interceptor falls back to the global `ratelimit.capacity` / `ratelimit.refillRate` from `application.yml`.

### Bucket key

`{user:<X-User-Id> | ip:<remoteAddr>}:<HTTP method>:<request URI>` — so every user/IP gets an independent bucket **per endpoint**, not one shared bucket across the whole API.

### Response headers / body

Allowed request:

```
HTTP/1.1 200 OK
X-RateLimit-Remaining: 4
X-RateLimit-Reset: 0
```

Denied request:

```
HTTP/1.1 429 Too Many Requests
Retry-After: 12
X-RateLimit-Reset: 12

{
  "status": 429,
  "error": "Too Many Requests",
  "message": "You have exceeded the rate limit. Please try again later.",
  "retryAfterSeconds": 12
}
```

---

## Configuration

`src/main/resources/application.yml`:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379

ratelimit:
  capacity: 10     # default bucket size, used when no @RateLimit is present
  refillRate: 1    # default tokens/sec
```

---

## Running locally

```bash
# 1. Start Redis
docker compose up -d

# 2. Run the app
./mvnw spring-boot:run
```

Try it (repeat quickly to trigger a 429):

```bash
curl -i -X POST http://localhost:8080/demo/login -H "X-User-Id: alice"
```

## Testing

```bash
./mvnw test
```

- `RateLimiterServiceApplicationTests` — Spring context loads.
- `RedisRateLimiterRepositoryTest` — spins up a real Redis via Testcontainers and asserts the Lua script allows exactly `capacity` requests then denies with a positive `retryAfterSeconds`.

---

## Benchmarks

Load testing (k6 script, throughput/latency/percent-blocked numbers under concurrent traffic) is a planned but not-yet-completed step — see `plan.MD` Phase 5. This section will be filled in with real numbers once `rate-limit-test.js` has been written and run against a running instance; no benchmark figures are included here to avoid publishing invented numbers.

---

## Project structure

```
src/main/java/com/cheese/ratelimiterservice/
├── annotation/    @RateLimit
├── config/        RedisConfig, WebConfig
├── controller/     DemoController
├── exception/      RateLimitExceededException, GlobalExceptionHandler
├── interceptor/     RateLimitInterceptor
├── ratelimit/       TokenBucket, RateLimitDecision, RateLimiterService
└── redis/           RedisRateLimiterRepository
src/main/resources/
├── application.yml
└── token_bucket.lua
```
