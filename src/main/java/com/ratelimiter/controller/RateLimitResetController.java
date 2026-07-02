package com.ratelimiter.controller;

import com.ratelimiter.service.RateLimiterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for manually resetting the rate limit count for an API key.
 *
 * <p>Resetting deletes the Redis Sorted Set containing all request timestamps
 * for the given API key, effectively giving it a fresh window.
 */
@RestController
@RequestMapping("/api/reset")
public class RateLimitResetController {

    private static final Logger log = LoggerFactory.getLogger(RateLimitResetController.class);

    private final RateLimiterService rateLimiterService;

    public RateLimitResetController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    /**
     * DELETE /api/reset/{apiKey}
     * Manually resets the rate limit count for the specified API key.
     *
     * <p>This clears the Redis Sorted Set (request log) for the given API key.
     * The API key's configuration (limit and window size) remains unchanged.
     * After reset, the API key can make requests up to its full limit again.
     *
     * @param apiKey Path variable identifying the API key to reset
     * @return HTTP 200 confirming the reset
     */
    @DeleteMapping("/{apiKey}")
    public ResponseEntity<Map<String, Object>> resetRateLimit(@PathVariable String apiKey) {
        log.info("Manual rate limit reset requested for apiKey={}", apiKey);

        boolean wasActive = rateLimiterService.resetRateLimit(apiKey);

        String message = wasActive
                ? "Rate limit count has been reset. The request log for this API key has been cleared."
                : "No active rate limit data found for this API key. It may already be at zero or the window expired.";

        return ResponseEntity.ok(Map.of(
                "message", message,
                "apiKey", apiKey,
                "wasActive", wasActive,
                "action", "RESET",
                "description", "The request log (Redis Sorted Set) for this API key has been cleared. " +
                               "Configuration (limit and window) is preserved."
        ));
    }

    /**
     * POST /api/reset/{apiKey}
     * Alias for DELETE - some clients cannot use DELETE method.
     */
    @PostMapping("/{apiKey}")
    public ResponseEntity<Map<String, Object>> resetRateLimitPost(@PathVariable String apiKey) {
        return resetRateLimit(apiKey);
    }
}
