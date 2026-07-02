package com.ratelimiter.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the rate limit configuration for an API key.
 * Stored as a Redis Hash with key: rate_limit:config:{apiKey}
 */
public class RateLimitConfig {

    @JsonProperty("apiKey")
    private String apiKey;

    @JsonProperty("limit")
    private int limit;

    @JsonProperty("windowSizeSeconds")
    private long windowSizeSeconds;

    // Default constructor for Jackson
    public RateLimitConfig() {}

    public RateLimitConfig(String apiKey, int limit, long windowSizeSeconds) {
        this.apiKey = apiKey;
        this.limit = limit;
        this.windowSizeSeconds = windowSizeSeconds;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public long getWindowSizeSeconds() {
        return windowSizeSeconds;
    }

    public void setWindowSizeSeconds(long windowSizeSeconds) {
        this.windowSizeSeconds = windowSizeSeconds;
    }

    @Override
    public String toString() {
        return "RateLimitConfig{apiKey='" + apiKey + "', limit=" + limit +
               ", windowSizeSeconds=" + windowSizeSeconds + "}";
    }
}
