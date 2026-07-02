package com.ratelimiter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the rate limiter.
 * Maps to "rate-limiter" prefix in application.yml.
 */
@Component
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimiterProperties {

    /**
     * Default maximum number of requests allowed per window for unconfigured API keys.
     */
    private int defaultLimit = 10;

    /**
     * Default sliding window size in seconds for unconfigured API keys.
     */
    private long defaultWindowSizeSeconds = 60;

    public int getDefaultLimit() {
        return defaultLimit;
    }

    public void setDefaultLimit(int defaultLimit) {
        this.defaultLimit = defaultLimit;
    }

    public long getDefaultWindowSizeSeconds() {
        return defaultWindowSizeSeconds;
    }

    public void setDefaultWindowSizeSeconds(long defaultWindowSizeSeconds) {
        this.defaultWindowSizeSeconds = defaultWindowSizeSeconds;
    }
}
