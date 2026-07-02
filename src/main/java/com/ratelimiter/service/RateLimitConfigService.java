package com.ratelimiter.service;

import com.ratelimiter.config.RateLimiterProperties;
import com.ratelimiter.model.RateLimitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service responsible for managing API key rate limit configurations.
 *
 * <p>Configurations are stored in Redis Hashes:
 * Key: {@code rate_limit:config:{apiKey}}
 * Fields: limit, windowSizeSeconds
 * </p>
 */
@Service
public class RateLimitConfigService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfigService.class);

    // Redis key prefix for storing configurations
    static final String CONFIG_KEY_PREFIX = "rate_limit:config:";

    private final RedisTemplate<String, String> redisTemplate;
    private final RateLimiterProperties properties;

    public RateLimitConfigService(RedisTemplate<String, String> redisTemplate,
                                  RateLimiterProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * Saves or updates the rate limit configuration for a given API key.
     * Stored as a Redis Hash at key: rate_limit:config:{apiKey}
     *
     * @param apiKey          The API key identifier
     * @param limit           Maximum number of requests per window
     * @param windowSizeSeconds The time window size in seconds
     * @return The saved RateLimitConfig
     */
    public RateLimitConfig saveConfig(String apiKey, int limit, long windowSizeSeconds) {
        String configKey = CONFIG_KEY_PREFIX + apiKey;

        // Store configuration as Redis Hash fields
        redisTemplate.opsForHash().put(configKey, "limit", String.valueOf(limit));
        redisTemplate.opsForHash().put(configKey, "windowSizeSeconds", String.valueOf(windowSizeSeconds));
        redisTemplate.opsForHash().put(configKey, "apiKey", apiKey);

        log.info("Saved rate limit config for apiKey={}: limit={}, window={}s",
                 apiKey, limit, windowSizeSeconds);

        return new RateLimitConfig(apiKey, limit, windowSizeSeconds);
    }

    /**
     * Retrieves the rate limit configuration for a given API key.
     * Returns default configuration if no custom config exists.
     *
     * @param apiKey The API key identifier
     * @return The RateLimitConfig (custom or default)
     */
    public RateLimitConfig getConfig(String apiKey) {
        String configKey = CONFIG_KEY_PREFIX + apiKey;

        Map<Object, Object> configMap = redisTemplate.opsForHash().entries(configKey);

        if (configMap == null || configMap.isEmpty()) {
            log.debug("No custom config found for apiKey={}, using defaults: limit={}, window={}s",
                      apiKey, properties.getDefaultLimit(), properties.getDefaultWindowSizeSeconds());
            return new RateLimitConfig(apiKey, properties.getDefaultLimit(),
                                       properties.getDefaultWindowSizeSeconds());
        }

        int limit = Integer.parseInt((String) configMap.get("limit"));
        long windowSizeSeconds = Long.parseLong((String) configMap.get("windowSizeSeconds"));

        log.debug("Found custom config for apiKey={}: limit={}, window={}s",
                  apiKey, limit, windowSizeSeconds);

        return new RateLimitConfig(apiKey, limit, windowSizeSeconds);
    }

    /**
     * Checks whether a custom configuration exists for the given API key.
     *
     * @param apiKey The API key
     * @return true if a custom config exists, false otherwise
     */
    public boolean hasCustomConfig(String apiKey) {
        String configKey = CONFIG_KEY_PREFIX + apiKey;
        Map<Object, Object> configMap = redisTemplate.opsForHash().entries(configKey);
        return configMap != null && !configMap.isEmpty();
    }

    /**
     * Deletes the custom configuration for a given API key.
     *
     * @param apiKey The API key
     */
    public void deleteConfig(String apiKey) {
        String configKey = CONFIG_KEY_PREFIX + apiKey;
        redisTemplate.delete(configKey);
        log.info("Deleted config for apiKey={}", apiKey);
    }
}
