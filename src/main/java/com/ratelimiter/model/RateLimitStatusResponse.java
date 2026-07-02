package com.ratelimiter.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for rate limit status and error responses.
 */
public class RateLimitStatusResponse {

    @JsonProperty("apiKey")
    private String apiKey;

    @JsonProperty("limit")
    private long limit;

    @JsonProperty("remaining")
    private long remaining;

    @JsonProperty("resetTimestampMs")
    private long resetTimestampMs;

    @JsonProperty("resetTimestampSeconds")
    private long resetTimestampSeconds;

    @JsonProperty("currentCount")
    private long currentCount;

    @JsonProperty("windowSizeSeconds")
    private long windowSizeSeconds;

    @JsonProperty("message")
    private String message;

    public RateLimitStatusResponse() {}

    public RateLimitStatusResponse(String apiKey, long limit, long remaining,
                                   long resetTimestampMs, long currentCount,
                                   long windowSizeSeconds) {
        this.apiKey = apiKey;
        this.limit = limit;
        this.remaining = remaining;
        this.resetTimestampMs = resetTimestampMs;
        this.resetTimestampSeconds = resetTimestampMs / 1000;
        this.currentCount = currentCount;
        this.windowSizeSeconds = windowSizeSeconds;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
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
        this.resetTimestampSeconds = resetTimestampMs / 1000;
    }

    public long getResetTimestampSeconds() {
        return resetTimestampSeconds;
    }

    public void setResetTimestampSeconds(long resetTimestampSeconds) {
        this.resetTimestampSeconds = resetTimestampSeconds;
    }

    public long getCurrentCount() {
        return currentCount;
    }

    public void setCurrentCount(long currentCount) {
        this.currentCount = currentCount;
    }

    public long getWindowSizeSeconds() {
        return windowSizeSeconds;
    }

    public void setWindowSizeSeconds(long windowSizeSeconds) {
        this.windowSizeSeconds = windowSizeSeconds;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
