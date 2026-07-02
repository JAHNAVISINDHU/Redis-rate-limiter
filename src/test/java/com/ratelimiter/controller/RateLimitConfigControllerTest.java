package com.ratelimiter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratelimiter.model.RateLimitConfig;
import com.ratelimiter.model.RateLimitConfigRequest;
import com.ratelimiter.service.RateLimitConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for the rate limit configuration endpoint.
 */
@WebMvcTest(RateLimitConfigController.class)
class RateLimitConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RateLimitConfigService configService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void configureRateLimit_shouldReturn201WithSavedConfig() throws Exception {
        // Given
        String apiKey = "my-api-key";
        RateLimitConfigRequest request = new RateLimitConfigRequest(100, 60);
        RateLimitConfig savedConfig = new RateLimitConfig(apiKey, 100, 60);

        when(configService.saveConfig(eq(apiKey), eq(100), eq(60L)))
                .thenReturn(savedConfig);

        // When & Then
        mockMvc.perform(post("/api/config/" + apiKey)
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.message").value("Rate limit configured successfully"))
               .andExpect(jsonPath("$.apiKey").value(apiKey))
               .andExpect(jsonPath("$.limit").value(100))
               .andExpect(jsonPath("$.windowSizeSeconds").value(60));
    }

    @Test
    void configureRateLimit_withInvalidLimit_shouldReturn400() throws Exception {
        // Given - limit = 0 is invalid
        String apiKey = "bad-request-key";
        RateLimitConfigRequest request = new RateLimitConfigRequest(0, 60);

        // When & Then
        mockMvc.perform(post("/api/config/" + apiKey)
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.error").value("limit must be greater than 0"));
    }

    @Test
    void configureRateLimit_withInvalidWindow_shouldReturn400() throws Exception {
        // Given - window = -1 is invalid
        String apiKey = "bad-window-key";
        RateLimitConfigRequest request = new RateLimitConfigRequest(10, -1);

        // When & Then
        mockMvc.perform(post("/api/config/" + apiKey)
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.error").value("windowSizeSeconds must be greater than 0"));
    }

    @Test
    void getConfig_shouldReturnCurrentConfig() throws Exception {
        // Given
        String apiKey = "existing-key";
        RateLimitConfig config = new RateLimitConfig(apiKey, 50, 120);

        when(configService.getConfig(apiKey)).thenReturn(config);
        when(configService.hasCustomConfig(apiKey)).thenReturn(true);

        // When & Then
        mockMvc.perform(get("/api/config/" + apiKey))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.apiKey").value(apiKey))
               .andExpect(jsonPath("$.limit").value(50))
               .andExpect(jsonPath("$.windowSizeSeconds").value(120))
               .andExpect(jsonPath("$.isCustomConfig").value(true));
    }

    @Test
    void deleteConfig_shouldReturn200WithConfirmation() throws Exception {
        // Given
        String apiKey = "key-to-delete";
        doNothing().when(configService).deleteConfig(apiKey);

        // When & Then
        mockMvc.perform(delete("/api/config/" + apiKey))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.apiKey").value(apiKey))
               .andExpect(jsonPath("$.message").value(
                       "Rate limit configuration deleted. Default limits will apply."));
    }
}
