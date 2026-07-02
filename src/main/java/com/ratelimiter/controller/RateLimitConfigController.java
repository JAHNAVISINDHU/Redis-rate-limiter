package com.ratelimiter.controller;

import com.ratelimiter.model.RateLimitConfig;
import com.ratelimiter.model.RateLimitConfigRequest;
import com.ratelimiter.service.RateLimitConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for managing rate limit configurations per API key.
 *
 * <p>Configurations are persisted in Redis Hashes and apply to all subsequent
 * requests for the given API key.
 */
@RestController
@RequestMapping("/api/config")
public class RateLimitConfigController {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfigController.class);

    private final RateLimitConfigService configService;

    public RateLimitConfigController(RateLimitConfigService configService) {
        this.configService = configService;
    }

    /**
     * POST /api/config/{apiKey}
     * Configures or updates the rate limit for a specific API key.
     * The configuration is persisted in Redis as a Hash.
     *
     * <p>Request body example:
     * <pre>
     * {
     *   "limit": 100,
     *   "windowSizeSeconds": 60
     * }
     * </pre>
     *
     * @param apiKey  Path variable identifying the API key
     * @param request The rate limit configuration
     * @return The saved configuration with HTTP 201 Created
     */
    @PostMapping("/{apiKey}")
    public ResponseEntity<Map<String, Object>> configureRateLimit(
            @PathVariable String apiKey,
            @RequestBody RateLimitConfigRequest request) {

        log.info("Configuring rate limit for apiKey={}: limit={}, window={}s",
                 apiKey, request.getLimit(), request.getWindowSizeSeconds());

        // Validate inputs
        if (request.getLimit() <= 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "limit must be greater than 0",
                    "apiKey", apiKey
            ));
        }
        if (request.getWindowSizeSeconds() <= 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "windowSizeSeconds must be greater than 0",
                    "apiKey", apiKey
            ));
        }

        RateLimitConfig savedConfig = configService.saveConfig(
                apiKey, request.getLimit(), request.getWindowSizeSeconds());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Rate limit configured successfully",
                "apiKey", savedConfig.getApiKey(),
                "limit", savedConfig.getLimit(),
                "windowSizeSeconds", savedConfig.getWindowSizeSeconds()
        ));
    }

    /**
     * GET /api/config/{apiKey}
     * Retrieves the current rate limit configuration for an API key.
     * Returns the default configuration if no custom config exists.
     *
     * @param apiKey Path variable identifying the API key
     * @return The rate limit configuration
     */
    @GetMapping("/{apiKey}")
    public ResponseEntity<Map<String, Object>> getConfig(@PathVariable String apiKey) {
        RateLimitConfig config = configService.getConfig(apiKey);
        boolean isCustom = configService.hasCustomConfig(apiKey);

        return ResponseEntity.ok(Map.of(
                "apiKey", config.getApiKey(),
                "limit", config.getLimit(),
                "windowSizeSeconds", config.getWindowSizeSeconds(),
                "isCustomConfig", isCustom
        ));
    }

    /**
     * DELETE /api/config/{apiKey}
     * Deletes the custom rate limit configuration for an API key.
     * After deletion, the API key will use the default configuration.
     *
     * @param apiKey Path variable identifying the API key
     * @return HTTP 200 with confirmation message
     */
    @DeleteMapping("/{apiKey}")
    public ResponseEntity<Map<String, Object>> deleteConfig(@PathVariable String apiKey) {
        configService.deleteConfig(apiKey);

        return ResponseEntity.ok(Map.of(
                "message", "Rate limit configuration deleted. Default limits will apply.",
                "apiKey", apiKey
        ));
    }
}
