package com.ratelimiter.controller;

import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitStatusResponse;
import com.ratelimiter.service.RateLimitConfigService;
import com.ratelimiter.service.RateLimiterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * REST Controller exposing the rate-limited protected endpoint.
 *
 * <p>Every request must include an {@code X-API-Key} header identifying the caller.
 * The Sliding Window Log algorithm is applied per API key using a Lua script.
 *
 * <p>Rate Limit Headers added to every response:
 * <ul>
 *   <li>{@code X-RateLimit-Limit}     — configured max requests per window</li>
 *   <li>{@code X-RateLimit-Remaining} — remaining requests in current window</li>
 *   <li>{@code X-RateLimit-Reset}     — Unix timestamp (seconds) when the window resets</li>
 *   <li>{@code X-RateLimit-Window}    — Window size in seconds</li>
 *   <li>{@code Retry-After}           — Seconds to wait before retrying (only on 429)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class RateLimitedController {

    private static final Logger log = LoggerFactory.getLogger(RateLimitedController.class);

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String DEFAULT_API_KEY = "default";

    private final RateLimiterService rateLimiterService;
    private final RateLimitConfigService configService;

    public RateLimitedController(RateLimiterService rateLimiterService,
                                 RateLimitConfigService configService) {
        this.rateLimiterService = rateLimiterService;
        this.configService = configService;
    }

    /**
     * GET /api/protected
     * A rate-limited endpoint that processes requests within configured limits.
     *
     * <p>Reads the {@code X-API-Key} header to identify the caller.
     * Falls back to "default" if no header is provided.
     * Returns HTTP 429 if the rate limit is exceeded.
     *
     * @param apiKey Optional API key from header (defaults to "default")
     * @return HTTP 200 with response data, or HTTP 429 if rate limited
     */
    @GetMapping("/protected")
    public ResponseEntity<Map<String, Object>> protectedEndpoint(
            @RequestHeader(value = API_KEY_HEADER, defaultValue = DEFAULT_API_KEY) String apiKey) {

        log.debug("Request to /api/protected from apiKey={}", apiKey);

        // Evaluate rate limit using Sliding Window Log algorithm (Lua script)
        RateLimitResult result = rateLimiterService.checkRateLimit(apiKey);

        // Build standard rate limit response headers
        HttpHeaders headers = buildRateLimitHeaders(result,
                configService.getConfig(apiKey).getWindowSizeSeconds());

        if (!result.isAllowed()) {
            // Calculate retry-after in seconds
            long retryAfterSeconds = Math.max(1,
                    (result.getResetTimestampMs() - System.currentTimeMillis()) / 1000);
            headers.set("Retry-After", String.valueOf(retryAfterSeconds));

            log.warn("Rate limit EXCEEDED for apiKey={}: count={}/{}",
                     apiKey, result.getCurrentCount(), result.getLimit());

            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .headers(headers)
                    .body(Map.of(
                            "error", "Too Many Requests",
                            "message", "Rate limit exceeded. Try again after " + retryAfterSeconds + " seconds.",
                            "apiKey", apiKey,
                            "limit", result.getLimit(),
                            "remaining", result.getRemaining(),
                            "resetAt", formatResetTime(result.getResetTimestampMs()),
                            "retryAfterSeconds", retryAfterSeconds
                    ));
        }

        log.debug("Request ALLOWED for apiKey={}: count={}/{}, remaining={}",
                  apiKey, result.getCurrentCount(), result.getLimit(), result.getRemaining());

        return ResponseEntity.ok()
                .headers(headers)
                .body(Map.of(
                        "message", "Request processed successfully",
                        "apiKey", apiKey,
                        "requestCount", result.getCurrentCount(),
                        "limit", result.getLimit(),
                        "remaining", result.getRemaining(),
                        "resetAt", formatResetTime(result.getResetTimestampMs()),
                        "timestamp", Instant.now().toString(),
                        "data", Map.of(
                                "status", "success",
                                "processedAt", System.currentTimeMillis()
                        )
                ));
    }

    /**
     * GET /api/protected/info
     * Returns information about the protected endpoint without consuming a rate limit slot.
     */
    @GetMapping("/protected/info")
    public ResponseEntity<Map<String, Object>> protectedEndpointInfo(
            @RequestHeader(value = API_KEY_HEADER, defaultValue = DEFAULT_API_KEY) String apiKey) {

        RateLimitResult status = rateLimiterService.getStatus(apiKey);
        long windowSizeSeconds = configService.getConfig(apiKey).getWindowSizeSeconds();
        HttpHeaders headers = buildRateLimitHeaders(status, windowSizeSeconds);

        return ResponseEntity.ok()
                .headers(headers)
                .body(Map.of(
                        "apiKey", apiKey,
                        "endpoint", "/api/protected",
                        "algorithm", "Sliding Window Log",
                        "limit", status.getLimit(),
                        "remaining", status.getRemaining(),
                        "currentCount", status.getCurrentCount(),
                        "windowSizeSeconds", windowSizeSeconds,
                        "resetAt", formatResetTime(status.getResetTimestampMs())
                ));
    }

    /**
     * Builds the standard HTTP rate limit response headers.
     */
    private HttpHeaders buildRateLimitHeaders(RateLimitResult result, long windowSizeSeconds) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Limit", String.valueOf(result.getLimit()));
        headers.set("X-RateLimit-Remaining", String.valueOf(result.getRemaining()));
        headers.set("X-RateLimit-Reset", String.valueOf(result.getResetTimestampSeconds()));
        headers.set("X-RateLimit-Window", String.valueOf(windowSizeSeconds));
        return headers;
    }

    /**
     * Formats a timestamp in milliseconds to a human-readable ISO-8601 string.
     */
    private String formatResetTime(long timestampMs) {
        return DateTimeFormatter.ISO_INSTANT
                .withZone(ZoneId.of("UTC"))
                .format(Instant.ofEpochMilli(timestampMs));
    }
}
