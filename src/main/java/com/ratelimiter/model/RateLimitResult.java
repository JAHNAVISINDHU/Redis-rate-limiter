package com.ratelimiter.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the result of a rate limit check.
 * Returned by the Lua script and used internally and in API responses.
 */
public class RateLimitResult {

    @JsonProperty("allowed")
    private boolean allowed;

    @JsonProperty("currentCount")
    private long currentCount;

    @JsonProperty("limit")
    private long limit;

    @JsonProperty("remaining")
    private long remaining;

    @JsonProperty("resetTimestampMs")
    private long resetTimestampMs;

    @JsonProperty("apiKey")
    private String apiKey;

    public RateLimitResult() {}

    public RateLimitResult(boolean allowed, long currentCount, long limit,
                           long remaining, long resetTimestampMs, String apiKey) {
        this.allowed = allowed;
        this.currentCount = currentCount;
        this.limit = limit;
        this.remaining = remaining;
        this.resetTimestampMs = resetTimestampMs;
        this.apiKey = apiKey;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    public long getCurrentCount() {
        return currentCount;
    }

    public void setCurrentCount(long currentCount) {
        this.currentCount = currentCount;
    }

    public long getLimit() {
        return limit;
    }

    public void setLimit(long limit) {
        this.limit = limit;
    }

    public long getRemaining() {
        return remaining;
    }

    public void setRemaining(long remaining) {
        this.remaining = remaining;
    }

    public long getResetTimestampMs() {
        return resetTimestampMs;
    }

    public void setResetTimestampMs(long resetTimestampMs) {
        this.resetTimestampMs = resetTimestampMs;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Returns the reset time in seconds (Unix epoch).
     */
    @JsonProperty("resetTimestampSeconds")
    public long getResetTimestampSeconds() {
        return resetTimestampMs / 1000;
    }

    @Override
    public String toString() {
        return "RateLimitResult{allowed=" + allowed + ", currentCount=" + currentCount +
               ", limit=" + limit + ", remaining=" + remaining +
               ", resetTimestampMs=" + resetTimestampMs + "}";
    }
}
