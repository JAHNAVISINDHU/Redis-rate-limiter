package com.ratelimiter.controller;

import com.ratelimiter.model.RateLimitConfig;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.service.RateLimitConfigService;
import com.ratelimiter.service.RateLimiterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for the rate-limited protected endpoint.
 * Verifies HTTP status codes, headers, and response bodies.
 */
@WebMvcTest(RateLimitedController.class)
class RateLimitedControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RateLimiterService rateLimiterService;

    @MockBean
    private RateLimitConfigService configService;

    @Test
    void protectedEndpoint_whenAllowed_shouldReturn200WithHeaders() throws Exception {
        // Given
        String apiKey = "valid-key";
        long resetMs = System.currentTimeMillis() + 60_000;
        RateLimitResult allowedResult = new RateLimitResult(true, 1L, 10L, 9L, resetMs, apiKey);
        RateLimitConfig config = new RateLimitConfig(apiKey, 10, 60);

        when(rateLimiterService.checkRateLimit(apiKey)).thenReturn(allowedResult);
        when(configService.getConfig(apiKey)).thenReturn(config);

        // When & Then
        mockMvc.perform(get("/api/protected").header("X-API-Key", apiKey))
               .andExpect(status().isOk())
               .andExpect(header().string("X-RateLimit-Limit", "10"))
               .andExpect(header().string("X-RateLimit-Remaining", "9"))
               .andExpect(header().exists("X-RateLimit-Reset"))
               .andExpect(header().string("X-RateLimit-Window", "60"))
               .andExpect(jsonPath("$.message").value("Request processed successfully"))
               .andExpect(jsonPath("$.apiKey").value(apiKey))
               .andExpect(jsonPath("$.remaining").value(9))
               .andExpect(jsonPath("$.limit").value(10));
    }

    @Test
    void protectedEndpoint_whenRateLimitExceeded_shouldReturn429WithRetryAfter() throws Exception {
        // Given
        String apiKey = "limited-key";
        long resetMs = System.currentTimeMillis() + 30_000;
        RateLimitResult deniedResult = new RateLimitResult(false, 10L, 10L, 0L, resetMs, apiKey);
        RateLimitConfig config = new RateLimitConfig(apiKey, 10, 60);

        when(rateLimiterService.checkRateLimit(apiKey)).thenReturn(deniedResult);
        when(configService.getConfig(apiKey)).thenReturn(config);

        // When & Then
        mockMvc.perform(get("/api/protected").header("X-API-Key", apiKey))
               .andExpect(status().isTooManyRequests())
               .andExpect(header().string("X-RateLimit-Limit", "10"))
               .andExpect(header().string("X-RateLimit-Remaining", "0"))
               .andExpect(header().exists("Retry-After"))
               .andExpect(jsonPath("$.error").value("Too Many Requests"))
               .andExpect(jsonPath("$.remaining").value(0))
               .andExpect(jsonPath("$.limit").value(10));
    }

    @Test
    void protectedEndpoint_withNoApiKey_shouldUseDefaultKey() throws Exception {
        // Given - no X-API-Key header, should use "default"
        long resetMs = System.currentTimeMillis() + 60_000;
        RateLimitResult allowedResult = new RateLimitResult(true, 1L, 10L, 9L, resetMs, "default");
        RateLimitConfig config = new RateLimitConfig("default", 10, 60);

        when(rateLimiterService.checkRateLimit("default")).thenReturn(allowedResult);
        when(configService.getConfig("default")).thenReturn(config);

        // When & Then
        mockMvc.perform(get("/api/protected"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.apiKey").value("default"));
    }

    @Test
    void protectedEndpoint_headersAlwaysPresent_evenOn429() throws Exception {
        // Given
        String apiKey = "throttled-key";
        long resetMs = System.currentTimeMillis() + 45_000;
        RateLimitResult deniedResult = new RateLimitResult(false, 10L, 10L, 0L, resetMs, apiKey);
        RateLimitConfig config = new RateLimitConfig(apiKey, 10, 60);

        when(rateLimiterService.checkRateLimit(apiKey)).thenReturn(deniedResult);
        when(configService.getConfig(apiKey)).thenReturn(config);

        // When & Then - verify all required headers are present on 429
        mockMvc.perform(get("/api/protected").header("X-API-Key", apiKey))
               .andExpect(status().isTooManyRequests())
               .andExpect(header().exists("X-RateLimit-Limit"))
               .andExpect(header().exists("X-RateLimit-Remaining"))
               .andExpect(header().exists("X-RateLimit-Reset"))
               .andExpect(header().exists("X-RateLimit-Window"))
               .andExpect(header().exists("Retry-After"));
    }

    @Test
    void protectedEndpoint_withDefaultLimit_shouldAllowUpToTenRequests() throws Exception {
        // Given - simulate last allowed request (10th)
        String apiKey = "tenth-request-key";
        long resetMs = System.currentTimeMillis() + 60_000;
        RateLimitResult tenthRequest = new RateLimitResult(true, 10L, 10L, 0L, resetMs, apiKey);
        RateLimitConfig config = new RateLimitConfig(apiKey, 10, 60);

        when(rateLimiterService.checkRateLimit(apiKey)).thenReturn(tenthRequest);
        when(configService.getConfig(apiKey)).thenReturn(config);

        // When & Then - 10th request should still be allowed
        mockMvc.perform(get("/api/protected").header("X-API-Key", apiKey))
               .andExpect(status().isOk())
               .andExpect(header().string("X-RateLimit-Remaining", "0"))
               .andExpect(jsonPath("$.remaining").value(0));
    }
}
