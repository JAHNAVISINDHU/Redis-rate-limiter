--[[
  sliding_window.lua
  ==================
  Atomic Sliding Window Log Rate Limiting Algorithm using Redis Sorted Sets.

  This Lua script executes atomically on the Redis server, ensuring no race
  conditions when multiple application instances check rate limits concurrently.

  Algorithm:
  ----------
  1. Determine the current timestamp (in milliseconds).
  2. Calculate the window start time by subtracting the window size from now.
  3. Remove all entries in the Sorted Set that fall outside the window (expired).
  4. Count the remaining entries (requests within the current window).
  5. If count < limit, add the current timestamp and allow the request.
  6. If count >= limit, reject the request with remaining time until reset.

  KEYS:
    KEYS[1] - The Sorted Set key for storing request timestamps
              Format: "rate_limit:{apiKey}"

  ARGV:
    ARGV[1] - Current timestamp in milliseconds (Unix epoch)
    ARGV[2] - Window size in milliseconds (e.g., 60000 for 60 seconds)
    ARGV[3] - Maximum number of requests allowed in the window (limit)

  Returns (array):
    [1] - allowed (1 = request allowed, 0 = request denied)
    [2] - current request count after this request (or at rejection time)
    [3] - limit (the configured maximum)
    [4] - remaining requests (limit - count, 0 if denied)
    [5] - reset timestamp in milliseconds (oldest entry + window size,
          or now + window size if empty)
--]]

-- Input parameters
local key            = KEYS[1]
local now            = tonumber(ARGV[1])
local window_ms      = tonumber(ARGV[2])
local limit          = tonumber(ARGV[3])

-- Calculate the start of the sliding window
local window_start   = now - window_ms

-- Step 1: Remove all timestamps outside the current window (expired entries)
-- ZREMRANGEBYSCORE removes members with score between -inf and window_start
redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)

-- Step 2: Count the number of requests currently in the window
local current_count  = redis.call('ZCARD', key)

-- Step 3: Determine the reset time
-- Reset time is when the oldest request in the window will expire
local reset_time
local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
if #oldest > 0 then
    -- The oldest entry timestamp + window size = when it expires
    reset_time = tonumber(oldest[2]) + window_ms
else
    -- No entries: reset time is now + window size
    reset_time = now + window_ms
end

-- Step 4: Check if the request is within the limit
if current_count < limit then
    -- ALLOWED: Add current timestamp to the Sorted Set
    -- Use timestamp as both score and member; add random suffix to handle
    -- multiple requests at the exact same millisecond
    local member = now .. '-' .. redis.call('INCR', key .. ':seq')
    redis.call('ZADD', key, now, member)

    -- Set TTL on the key so it auto-expires (window_ms + buffer in seconds)
    local ttl_seconds = math.ceil((window_ms / 1000) + 1)
    redis.call('EXPIRE', key, ttl_seconds)

    -- Also set TTL on the sequence counter key
    redis.call('EXPIRE', key .. ':seq', ttl_seconds)

    local new_count   = current_count + 1
    local remaining   = limit - new_count

    -- Update reset time now that we've added an entry
    if new_count == 1 then
        reset_time = now + window_ms
    end

    return {1, new_count, limit, remaining, reset_time}
else
    -- DENIED: Too many requests
    local remaining = 0

    return {0, current_count, limit, remaining, reset_time}
end
