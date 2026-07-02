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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for the rate limit status endpoint.
 */
@WebMvcTest(RateLimitStatusController.class)
class RateLimitStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RateLimiterService rateLimiterService;

    @MockBean
    private RateLimitConfigService configService;

    @Test
    void getStatus_shouldReturnCurrentWindowDetails() throws Exception {
        // Given
        String apiKey = "status-key";
        long resetMs = System.currentTimeMillis() + 45_000;
        RateLimitResult result = new RateLimitResult(true, 3L, 10L, 7L, resetMs, apiKey);
        RateLimitConfig config = new RateLimitConfig(apiKey, 10, 60);

        when(rateLimiterService.getStatus(apiKey)).thenReturn(result);
        when(configService.getConfig(apiKey)).thenReturn(config);

        // When & Then
        mockMvc.perform(get("/api/status/" + apiKey))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.apiKey").value(apiKey))
               .andExpect(jsonPath("$.limit").value(10))
               .andExpect(jsonPath("$.remaining").value(7))
               .andExpect(jsonPath("$.currentCount").value(3))
               .andExpect(jsonPath("$.windowSizeSeconds").value(60))
               .andExpect(jsonPath("$.resetTimestampMs").exists())
               .andExpect(jsonPath("$.resetTimestampSeconds").exists())
               .andExpect(jsonPath("$.message").value("API key is within rate limits."));
    }

    @Test
    void getStatus_whenRateLimitExceeded_shouldReturnExceededMessage() throws Exception {
        // Given
        String apiKey = "exceeded-key";
        long resetMs = System.currentTimeMillis() + 30_000;
        RateLimitResult result = new RateLimitResult(false, 10L, 10L, 0L, resetMs, apiKey);
        RateLimitConfig config = new RateLimitConfig(apiKey, 10, 60);

        when(rateLimiterService.getStatus(apiKey)).thenReturn(result);
        when(configService.getConfig(apiKey)).thenReturn(config);

        // When & Then
        mockMvc.perform(get("/api/status/" + apiKey))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.remaining").value(0))
               .andExpect(jsonPath("$.currentCount").value(10))
               .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Rate limit exceeded")));
    }

    @Test
    void getStatus_serviceStatus_shouldReturnServiceInfo() throws Exception {
        mockMvc.perform(get("/api/status"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.service").value("Redis Rate Limiter"))
               .andExpect(jsonPath("$.status").value("UP"))
               .andExpect(jsonPath("$.algorithm").value("Sliding Window Log"));
    }
}
