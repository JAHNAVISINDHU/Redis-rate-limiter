package com.ratelimiter.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratelimiter.model.RateLimitConfigRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import redis.embedded.RedisServer;

import java.io.IOException;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests for the Rate Limiter service.
 *
 * <p>These tests use an embedded Redis instance and exercise the complete
 * request lifecycle: configuration → rate limiting → status check → reset.
 *
 * <p>Key scenarios tested:
 * <ul>
 *   <li>Default limit of 10 requests per minute for unconfigured keys</li>
 *   <li>Custom configured limits and windows</li>
 *   <li>HTTP 429 returned when limit is exceeded</li>
 *   <li>Rate limit headers present on every response</li>
 *   <li>Status endpoint returns correct remaining count</li>
 *   <li>Reset endpoint clears the request log</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RateLimiterIntegrationTest {

    private static RedisServer redisServer;
    private static final int EMBEDDED_REDIS_PORT = 6375;

    @BeforeAll
    static void startRedis() throws IOException {
        redisServer = new RedisServer(EMBEDDED_REDIS_PORT);
        redisServer.start();
    }

    @AfterAll
    static void stopRedis() throws IOException {
        if (redisServer != null && redisServer.isActive()) {
            redisServer.stop();
        }
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.port", () -> EMBEDDED_REDIS_PORT);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanUpRedis() {
        // Clean up all rate limit keys before each test
        var keys = redisTemplate.keys("rate_limit:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // =========================================================================
    // 1. Default Rate Limit Tests
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("Default limit: unconfigured key should allow up to 10 requests per minute")
    void defaultLimit_allowsUpToTenRequests() throws Exception {
        String apiKey = "default-test-key-" + System.currentTimeMillis();

        // Make 10 requests - all should succeed
        for (int i = 1; i <= 10; i++) {
            mockMvc.perform(get("/api/protected").header("X-API-Key", apiKey))
                   .andExpect(status().isOk())
                   .andExpect(header().string("X-RateLimit-Limit", "10"))
                   .andExpect(jsonPath("$.message").value("Request processed successfully"));
        }

        // 11th request should be rate limited
        mockMvc.perform(get("/api/protected").header("X-API-Key", apiKey))
               .andExpect(status().isTooManyRequests())
               .andExpect(header().string("X-RateLimit-Limit", "10"))
               .andExpect(header().string("X-RateLimit-Remaining", "0"))
               .andExpect(header().exists("Retry-After"))
               .andExpect(jsonPath("$.error").value("Too Many Requests"));
    }

    // =========================================================================
    // 2. Custom Configuration Tests
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("Custom config: persisted in Redis and applied to subsequent requests")
    void customConfig_persistedAndApplied() throws Exception {
        String apiKey = "custom-test-key-" + System.currentTimeMillis();

        // Configure a limit of 3 requests per 30 seconds
        RateLimitConfigRequest configRequest = new RateLimitConfigRequest(3, 30);
        mockMvc.perform(post("/api/config/" + apiKey)
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(configRequest)))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.limit").value(3))
               .andExpect(jsonPath("$.windowSizeSeconds").value(30));

        // Make 3 requests - all should succeed with custom limit
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(get("/api/protected").header("X-API-Key", apiKey))
                   .andExpect(status().isOk())
                   .andExpect(header().string("X-RateLimit-Limit", "3"));
        }

        // 4th request should be rate limited
        mockMvc.perform(get("/api/protected").header("X-API-Key", apiKey))
               .andExpect(status().isTooManyRequests())
               .andExpect(header().string("X-RateLimit-Limit", "3"))
               .andExpect(header().string("X-RateLimit-Remaining", "0"));
    }

    // =========================================================================
    // 3. Rate Limit Headers Tests
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("Rate limit headers: present in every response including 429")
    void rateLimitHeaders_presentOnEveryResponse() throws Exception {
        String apiKey = "headers-test-key-" + System.currentTimeMillis();

        // Normal response should have all headers
        mockMvc.perform(get("/api/protected").header("X-API-Key", apiKey))
               .andExpect(status().isOk())
               .andExpect(header().exists("X-RateLimit-Limit"))
               .andExpect(header().exists("X-RateLimit-Remaining"))
               .andExpect(header().exists("X-RateLimit-Reset"))
               .andExpect(header().exists("X-RateLimit-Window"));

        // Remaining should decrease with each request
        mockMvc.perform(get("/api/protected").header("X-API-Key", apiKey))
               .andExpect(status().isOk())
               .andExpect(header().string("X-RateLimit-Remaining", "8")); // 10 - 2 = 8
    }

    // =========================================================================
    // 4. Status Endpoint Tests
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("Status endpoint: returns accurate remaining count without consuming slot")
    void statusEndpoint_returnsAccurateStateWithoutConsumingSlot() throws Exception {
        String apiKey = "status-test-key-" + System.currentTimeMillis();

        // Make 5 requests
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/protected").header("X-API-Key", apiKey))
                   .andExpect(status().isOk());
        }

        // Status should show 5 used, 5 remaining, without consuming another slot
        mockMvc.perform(get("/api/status/" + apiKey))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.apiKey").value(apiKey))
               .andExpect(jsonPath("$.limit").value(10))
               .andExpect(jsonPath("$.currentCount").value(5))
               .andExpect(jsonPath("$.remaining").value(5))
               .andExpect(jsonPath("$.resetTimestampMs").isNumber())
               .andExpect(jsonPath("$.resetTimestampSeconds").isNumber())
               .andExpect(jsonPath("$.windowSizeSeconds").value(60))
               .andExpect(jsonPath("$.message").value("API key is within rate limits."));

        // Verify status check did not consume a slot (still 5 requests used)
        mockMvc.perform(get("/api/status/" + apiKey))
               .andExpect(jsonPath("$.currentCount").value(5));
    }

    // =========================================================================
    // 5. Reset Endpoint Tests
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("Reset endpoint: clears request log allowing full limit again")
    void resetEndpoint_clearsRequestLogAndAllowsFullLimit() throws Exception {
        String apiKey = "reset-test-key-" + System.currentTimeMillis();

        // Exhaust the default limit of 10
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/protected").header("X-API-Key", apiKey))
                   .andExpect(status().isOk());
        }

        // Verify limit is exceeded
        mockMvc.perform(get("/api/protected").header("X-API-Key", apiKey))
               .andExpect(status().isTooManyRequests());

        // Reset the rate limit
        mockMvc.perform(delete("/api/reset/" + apiKey))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.apiKey").value(apiKey))
               .andExpect(jsonPath("$.action").value("RESET"))
               .andExpect(jsonPath("$.wasActive").value(true));

        // After reset, requests should be allowed again
        mockMvc.perform(get("/api/protected").header("X-API-Key", apiKey))
               .andExpect(status().isOk())
               .andExpect(header().string("X-RateLimit-Remaining", "9"))
               .andExpect(jsonPath("$.message").value("Request processed successfully"));
    }

    // =========================================================================
    // 6. Config Endpoint Tests
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("Config endpoint: GET retrieves stored configuration")
    void configEndpoint_getReturnsStoredConfiguration() throws Exception {
        String apiKey = "config-get-key-" + System.currentTimeMillis();

        // Configure
        RateLimitConfigRequest configRequest = new RateLimitConfigRequest(200, 3600);
        mockMvc.perform(post("/api/config/" + apiKey)
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(configRequest)))
               .andExpect(status().isCreated());

        // Retrieve
        mockMvc.perform(get("/api/config/" + apiKey))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.apiKey").value(apiKey))
               .andExpect(jsonPath("$.limit").value(200))
               .andExpect(jsonPath("$.windowSizeSeconds").value(3600))
               .andExpect(jsonPath("$.isCustomConfig").value(true));
    }

    @Test
    @Order(7)
    @DisplayName("Config endpoint: unconfigured key returns defaults")
    void configEndpoint_unconfiguredKeyReturnsDefaults() throws Exception {
        String apiKey = "unconfigured-key-" + System.currentTimeMillis();

        mockMvc.perform(get("/api/config/" + apiKey))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.limit").value(10))
               .andExpect(jsonPath("$.windowSizeSeconds").value(60))
               .andExpect(jsonPath("$.isCustomConfig").value(false));
    }

    // =========================================================================
    // 7. Sliding Window Accuracy Tests
    // =========================================================================

    @Test
    @Order(8)
    @DisplayName("Sliding window: remaining count decreases correctly per request")
    void slidingWindow_remainingDecreaseCorrectly() throws Exception {
        String apiKey = "sliding-test-key-" + System.currentTimeMillis();

        // First request: remaining = 9
        mockMvc.perform(get("/api/protected").header("X-API-Key", apiKey))
               .andExpect(status().isOk())
               .andExpect(header().string("X-RateLimit-Remaining", "9"))
               .andExpect(jsonPath("$.remaining").value(9));

        // Second request: remaining = 8
        mockMvc.perform(get("/api/protected").header("X-API-Key", apiKey))
               .andExpect(status().isOk())
               .andExpect(header().string("X-RateLimit-Remaining", "8"))
               .andExpect(jsonPath("$.remaining").value(8));

        // Third request: remaining = 7
        mockMvc.perform(get("/api/protected").header("X-API-Key", apiKey))
               .andExpect(status().isOk())
               .andExpect(header().string("X-RateLimit-Remaining", "7"))
               .andExpect(jsonPath("$.remaining").value(7));
    }

    @Test
    @Order(9)
    @DisplayName("429 response: includes resetAt timestamp for client backoff")
    void exceededResponse_includesResetTimestamp() throws Exception {
        String apiKey = "reset-ts-key-" + System.currentTimeMillis();

        // Exhaust limit
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/protected").header("X-API-Key", apiKey));
        }

        // 429 should include reset information
        mockMvc.perform(get("/api/protected").header("X-API-Key", apiKey))
               .andExpect(status().isTooManyRequests())
               .andExpect(jsonPath("$.resetAt").exists())
               .andExpect(jsonPath("$.retryAfterSeconds").isNumber())
               .andExpect(header().exists("Retry-After"));
    }
}
