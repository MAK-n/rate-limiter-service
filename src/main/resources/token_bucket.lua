-- KEYS[1] = bucket key
-- ARGV[1] = capacity
-- ARGV[2] = refill rate (tokens per second)
-- ARGV[3] = bucket TTL in seconds (for removing idle buckets)

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refillRate = tonumber(ARGV[2])
local ttl = tonumber(ARGV[3])

local time = redis.call("TIME")
local nowMs = tonumber(time[1]) * 1000 + tonumber(time[2]) / 1000

local bucket = redis.call("HMGET", key, "tokens", "lastRefill")
local tokens = tonumber(bucket[1])
local lastRefill = tonumber(bucket[2])

if tokens == nil then
    tokens = capacity
    lastRefill = nowMs
end

local timeSinceLastRefillSeconds = (nowMs - lastRefill) / 1000
tokens = math.min(capacity, tokens + timeSinceLastRefillSeconds * refillRate)

local allowed = 0
local retryAfter = 0
if tokens >= 1 then
    tokens = tokens - 1
    allowed = 1
else
    retryAfter = math.max(1, math.ceil((1 - tokens) / refillRate))
end

-- seconds until at least one token is available again (0 if already available)
local resetSeconds = 0
if tokens < 1 then
    resetSeconds = math.max(1, math.ceil((1 - tokens) / refillRate))
end

redis.call("HSET", key, "tokens", tokens, "lastRefill", nowMs)
redis.call("EXPIRE", key, ttl)

return {allowed, math.floor(tokens), retryAfter, resetSeconds}
