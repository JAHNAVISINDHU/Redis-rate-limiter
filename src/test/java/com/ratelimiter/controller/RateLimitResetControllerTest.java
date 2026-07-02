package com.ratelimiter.controller;

import com.ratelimiter.service.RateLimiterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for the rate limit reset endpoint.
 */
@WebMvcTest(RateLimitResetController.class)
class RateLimitResetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RateLimiterService rateLimiterService;

    @Test
    void resetRateLimit_whenKeyActive_shouldReturn200WithActiveConfirmation() throws Exception {
        // Given
        String apiKey = "active-key";
        when(rateLimiterService.resetRateLimit(apiKey)).thenReturn(true);

        // When & Then
        mockMvc.perform(delete("/api/reset/" + apiKey))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.apiKey").value(apiKey))
               .andExpect(jsonPath("$.action").value("RESET"))
               .andExpect(jsonPath("$.wasActive").value(true))
               .andExpect(jsonPath("$.message").value(
                       "Rate limit count has been reset. The request log for this API key has been cleared."));
    }

    @Test
    void resetRateLimit_whenKeyNotActive_shouldReturn200WithInactiveMessage() throws Exception {
        // Given
        String apiKey = "inactive-key";
        when(rateLimiterService.resetRateLimit(apiKey)).thenReturn(false);

        // When & Then
        mockMvc.perform(delete("/api/reset/" + apiKey))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.apiKey").value(apiKey))
               .andExpect(jsonPath("$.wasActive").value(false))
               .andExpect(jsonPath("$.message").value(
                       "No active rate limit data found for this API key. " +
                       "It may already be at zero or the window expired."));
    }

    @Test
    void resetRateLimit_viaPost_shouldAlsoWork() throws Exception {
        // Given - POST alias for DELETE
        String apiKey = "post-reset-key";
        when(rateLimiterService.resetRateLimit(apiKey)).thenReturn(true);

        // When & Then
        mockMvc.perform(post("/api/reset/" + apiKey))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.action").value("RESET"))
               .andExpect(jsonPath("$.apiKey").value(apiKey));
    }

    @Test
    void resetRateLimit_responseContainsDescription() throws Exception {
        // Given
        String apiKey = "desc-key";
        when(rateLimiterService.resetRateLimit(apiKey)).thenReturn(true);

        // When & Then - response should explain what was cleared
        mockMvc.perform(delete("/api/reset/" + apiKey))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.description").value(
                       org.hamcrest.Matchers.containsString("Sorted Set")));
    }
}
