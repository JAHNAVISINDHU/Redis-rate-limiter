package com.ratelimiter.service;

import com.ratelimiter.model.RateLimitConfig;
import com.ratelimiter.model.RateLimitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Core rate limiting service implementing the Sliding Window Log algorithm.
 *
 * <p>Uses Redis Sorted Sets to store request timestamps and a Lua script for
 * atomic evaluation of rate limits. The Lua script ensures that concurrent
 * requests across multiple instances are handled correctly without race conditions.
 *
 * <p>Redis Key Structure:
 * <ul>
 *   <li>{@code rate_limit:{apiKey}} - Sorted Set of request timestamps (score = timestamp ms)</li>
 *   <li>{@code rate_limit:{apiKey}:seq} - Sequence counter for unique member names</li>
 * </ul>
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    // Redis key prefix for rate limit Sorted Sets
    static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<List> slidingWindowScript;
    private final RateLimitConfigService configService;

    public RateLimiterService(RedisTemplate<String, String> redisTemplate,
                              DefaultRedisScript<List> slidingWindowScript,
                              RateLimitConfigService configService) {
        this.redisTemplate = redisTemplate;
        this.slidingWindowScript = slidingWindowScript;
        this.configService = configService;
    }

    /**
     * Evaluates whether a request from the given API key is within rate limits.
     *
     * <p>Executes the sliding_window.lua script atomically on Redis, which:
     * <ol>
     *   <li>Removes expired timestamps from the Sorted Set</li>
     *   <li>Counts current requests in the window</li>
     *   <li>Allows or denies based on the configured limit</li>
     *   <li>If allowed, records the current timestamp</li>
     * </ol>
     *
     * @param apiKey The API key making the request
     * @return RateLimitResult containing allow/deny decision and metadata
     */
    public RateLimitResult checkRateLimit(String apiKey) {
        RateLimitConfig config = configService.getConfig(apiKey);
        String redisKey = RATE_LIMIT_KEY_PREFIX + apiKey;

        long nowMs = System.currentTimeMillis();
        long windowMs = config.getWindowSizeSeconds() * 1000L;
        long limit = config.getLimit();

        log.debug("Checking rate limit for apiKey={}: key={}, limit={}, windowMs={}",
                  apiKey, redisKey, limit, windowMs);

        try {
            // Execute Lua script atomically
            // KEYS[1] = rate limit sorted set key
            // ARGV[1] = current timestamp in ms
            // ARGV[2] = window size in ms
            // ARGV[3] = limit
            List<?> result = redisTemplate.execute(
                    slidingWindowScript,
                    Collections.singletonList(redisKey),
                    String.valueOf(nowMs),
                    String.valueOf(windowMs),
                    String.valueOf(limit)
            );

            return parseScriptResult(result, apiKey);

        } catch (Exception e) {
            log.error("Error executing rate limit Lua script for apiKey={}: {}", apiKey, e.getMessage(), e);
            // Fail open: allow the request if Redis is unavailable
            return new RateLimitResult(true, 0, limit, limit, nowMs + windowMs, apiKey);
        }
    }

    /**
     * Retrieves the current rate limit status for an API key WITHOUT consuming a request.
     *
     * <p>This is a read-only operation that:
     * <ol>
     *   <li>Cleans expired entries from the Sorted Set</li>
     *   <li>Counts current requests in window</li>
     *   <li>Returns status without adding a new entry</li>
     * </ol>
     *
     * @param apiKey The API key to check
     * @return RateLimitResult with current status
     */
    public RateLimitResult getStatus(String apiKey) {
        RateLimitConfig config = configService.getConfig(apiKey);
        String redisKey = RATE_LIMIT_KEY_PREFIX + apiKey;

        long nowMs = System.currentTimeMillis();
        long windowMs = config.getWindowSizeSeconds() * 1000L;
        long windowStart = nowMs - windowMs;
        long limit = config.getLimit();

        try {
            // Remove expired entries first (cleanup)
            redisTemplate.opsForZSet().removeRangeByScore(redisKey, Double.NEGATIVE_INFINITY, windowStart);

            // Count current requests in window
            Long count = redisTemplate.opsForZSet().count(redisKey, windowStart, Double.POSITIVE_INFINITY);
            long currentCount = count != null ? count : 0;

            // Find reset time: oldest entry's timestamp + window size
            Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> oldest =
                    redisTemplate.opsForZSet().rangeWithScores(redisKey, 0, 0);

            long resetTimestampMs;
            if (oldest != null && !oldest.isEmpty()) {
                Double oldestScore = oldest.iterator().next().getScore();
                resetTimestampMs = (oldestScore != null ? oldestScore.longValue() : nowMs) + windowMs;
            } else {
                resetTimestampMs = nowMs + windowMs;
            }

            long remaining = Math.max(0, limit - currentCount);

            log.debug("Status for apiKey={}: count={}, limit={}, remaining={}, resetAt={}",
                      apiKey, currentCount, limit, remaining, resetTimestampMs);

            return new RateLimitResult(currentCount < limit, currentCount, limit,
                                       remaining, resetTimestampMs, apiKey);

        } catch (Exception e) {
            log.error("Error getting rate limit status for apiKey={}: {}", apiKey, e.getMessage(), e);
            return new RateLimitResult(true, 0, limit, limit, nowMs + windowMs, apiKey);
        }
    }

    /**
     * Resets the rate limit for a given API key by deleting the Sorted Set.
     *
     * @param apiKey The API key to reset
     * @return true if the key existed and was deleted, false if it didn't exist
     */
    public boolean resetRateLimit(String apiKey) {
        String redisKey = RATE_LIMIT_KEY_PREFIX + apiKey;
        String seqKey = redisKey + ":seq";

        Boolean keyDeleted = redisTemplate.delete(redisKey);
        redisTemplate.delete(seqKey); // Also clean up sequence counter

        boolean wasReset = Boolean.TRUE.equals(keyDeleted);
        log.info("Reset rate limit for apiKey={}: keyExisted={}", apiKey, wasReset);

        return wasReset;
    }

    /**
     * Parses the List result returned by the Lua script into a RateLimitResult.
     *
     * <p>Script return format: [allowed, currentCount, limit, remaining, resetTimestampMs]
     *
     * @param result The raw result from Redis script execution
     * @param apiKey The API key for context
     * @return Parsed RateLimitResult
     */
    private RateLimitResult parseScriptResult(List<?> result, String apiKey) {
        if (result == null || result.size() < 5) {
            log.warn("Unexpected script result for apiKey={}: {}", apiKey, result);
            return new RateLimitResult(false, 0, 0, 0, System.currentTimeMillis(), apiKey);
        }

        boolean allowed = toLong(result.get(0)) == 1L;
        long currentCount = toLong(result.get(1));
        long limit = toLong(result.get(2));
        long remaining = toLong(result.get(3));
        long resetTimestampMs = toLong(result.get(4));

        log.debug("Script result for apiKey={}: allowed={}, count={}, limit={}, remaining={}, reset={}",
                  apiKey, allowed, currentCount, limit, remaining, resetTimestampMs);

        return new RateLimitResult(allowed, currentCount, limit, remaining, resetTimestampMs, apiKey);
    }

    /**
     * Safely converts a value from the Lua script result to a long.
     */
    private long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) return Long.parseLong(s);
        return 0L;
    }
}
