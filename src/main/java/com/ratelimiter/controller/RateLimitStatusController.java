package com.ratelimiter.controller;

import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitStatusResponse;
import com.ratelimiter.service.RateLimitConfigService;
import com.ratelimiter.service.RateLimiterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for querying rate limit status of an API key.
 *
 * <p>The status check is READ-ONLY — it does NOT consume a request slot.
 * It cleans expired entries and returns the current window state.
 */
@RestController
@RequestMapping("/api/status")
public class RateLimitStatusController {

    private static final Logger log = LoggerFactory.getLogger(RateLimitStatusController.class);

    private final RateLimiterService rateLimiterService;
    private final RateLimitConfigService configService;

    public RateLimitStatusController(RateLimiterService rateLimiterService,
                                     RateLimitConfigService configService) {
        this.rateLimiterService = rateLimiterService;
        this.configService = configService;
    }

    /**
     * GET /api/status/{apiKey}
     * Returns the current rate limit status for the given API key.
     *
     * <p>The response includes:
     * <ul>
     *   <li>{@code limit}               — Total request limit per window</li>
     *   <li>{@code remaining}           — Remaining requests in the current window</li>
     *   <li>{@code currentCount}        — Number of requests already made in the window</li>
     *   <li>{@code resetTimestampMs}    — Unix timestamp in ms when the window resets</li>
     *   <li>{@code resetTimestampSeconds} — Unix timestamp in seconds</li>
     *   <li>{@code windowSizeSeconds}   — The configured time window size</li>
     * </ul>
     *
     * @param apiKey Path variable identifying the API key
     * @return HTTP 200 with rate limit status details
     */
    @GetMapping("/{apiKey}")
    public ResponseEntity<RateLimitStatusResponse> getStatus(@PathVariable String apiKey) {
        log.debug("Status check requested for apiKey={}", apiKey);

        // Read-only status check (does not consume a request slot)
        RateLimitResult result = rateLimiterService.getStatus(apiKey);
        long windowSizeSeconds = configService.getConfig(apiKey).getWindowSizeSeconds();

        RateLimitStatusResponse response = new RateLimitStatusResponse(
                apiKey,
                result.getLimit(),
                result.getRemaining(),
                result.getResetTimestampMs(),
                result.getCurrentCount(),
                windowSizeSeconds
        );

        // Set a human-readable message
        if (result.isAllowed()) {
            response.setMessage("API key is within rate limits.");
        } else {
            long retryAfterSeconds = Math.max(0,
                    (result.getResetTimestampMs() - System.currentTimeMillis()) / 1000);
            response.setMessage("Rate limit exceeded. Retry after " + retryAfterSeconds + " seconds.");
        }

        log.debug("Status for apiKey={}: count={}, limit={}, remaining={}",
                  apiKey, result.getCurrentCount(), result.getLimit(), result.getRemaining());

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/status
     * Returns a general health status of the rate limiter service.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> serviceStatus() {
        return ResponseEntity.ok(Map.of(
                "service", "Redis Rate Limiter",
                "status", "UP",
                "algorithm", "Sliding Window Log",
                "storage", "Redis Sorted Sets",
                "description", "Use GET /api/status/{apiKey} to check a specific API key's status"
        ));
    }
}
