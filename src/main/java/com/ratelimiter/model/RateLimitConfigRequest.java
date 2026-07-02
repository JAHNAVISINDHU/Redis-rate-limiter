package com.ratelimiter.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body DTO for configuring rate limits via the API.
 */
public class RateLimitConfigRequest {

    @JsonProperty("limit")
    private int limit;

    @JsonProperty("windowSizeSeconds")
    private long windowSizeSeconds;

    public RateLimitConfigRequest() {}

    public RateLimitConfigRequest(int limit, long windowSizeSeconds) {
        this.limit = limit;
        this.windowSizeSeconds = windowSizeSeconds;
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
}
