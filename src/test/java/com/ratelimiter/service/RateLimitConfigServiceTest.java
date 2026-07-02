package com.ratelimiter.service;

import com.ratelimiter.config.RateLimiterProperties;
import com.ratelimiter.model.RateLimitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RateLimitConfigService.
 * Tests Redis Hash storage and retrieval of rate limit configurations.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitConfigServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private RateLimiterProperties properties;

    private RateLimitConfigService configService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        configService = new RateLimitConfigService(redisTemplate, properties);
    }

    @Test
    void saveConfig_shouldStoreConfigInRedisHash() {
        // Given
        String apiKey = "test-key-001";
        int limit = 100;
        long windowSeconds = 60;

        // When
        RateLimitConfig saved = configService.saveConfig(apiKey, limit, windowSeconds);

        // Then
        assertThat(saved).isNotNull();
        assertThat(saved.getApiKey()).isEqualTo(apiKey);
        assertThat(saved.getLimit()).isEqualTo(limit);
        assertThat(saved.getWindowSizeSeconds()).isEqualTo(windowSeconds);

        // Verify Redis hash operations were called
        verify(hashOperations).put(
                eq(RateLimitConfigService.CONFIG_KEY_PREFIX + apiKey),
                eq("limit"),
                eq("100")
        );
        verify(hashOperations).put(
                eq(RateLimitConfigService.CONFIG_KEY_PREFIX + apiKey),
                eq("windowSizeSeconds"),
                eq("60")
        );
    }

    @Test
    void getConfig_withExistingConfig_shouldReturnCustomConfig() {
        // Given
        String apiKey = "existing-key";
        Map<Object, Object> configMap = new HashMap<>();
        configMap.put("limit", "50");
        configMap.put("windowSizeSeconds", "120");
        configMap.put("apiKey", apiKey);

        when(hashOperations.entries(RateLimitConfigService.CONFIG_KEY_PREFIX + apiKey))
                .thenReturn(configMap);

        // When
        RateLimitConfig config = configService.getConfig(apiKey);

        // Then
        assertThat(config.getApiKey()).isEqualTo(apiKey);
        assertThat(config.getLimit()).isEqualTo(50);
        assertThat(config.getWindowSizeSeconds()).isEqualTo(120);
    }

    @Test
    void getConfig_withNoConfig_shouldReturnDefaults() {
        // Given
        String apiKey = "unconfigured-key";
        when(hashOperations.entries(anyString())).thenReturn(new HashMap<>());
        when(properties.getDefaultLimit()).thenReturn(10);
        when(properties.getDefaultWindowSizeSeconds()).thenReturn(60L);

        // When
        RateLimitConfig config = configService.getConfig(apiKey);

        // Then
        assertThat(config.getApiKey()).isEqualTo(apiKey);
        assertThat(config.getLimit()).isEqualTo(10);
        assertThat(config.getWindowSizeSeconds()).isEqualTo(60);
    }

    @Test
    void hasCustomConfig_withExistingConfig_shouldReturnTrue() {
        // Given
        String apiKey = "existing-key";
        Map<Object, Object> configMap = new HashMap<>();
        configMap.put("limit", "50");
        when(hashOperations.entries(anyString())).thenReturn(configMap);

        // When
        boolean hasConfig = configService.hasCustomConfig(apiKey);

        // Then
        assertThat(hasConfig).isTrue();
    }

    @Test
    void hasCustomConfig_withNoConfig_shouldReturnFalse() {
        // Given
        String apiKey = "new-key";
        when(hashOperations.entries(anyString())).thenReturn(new HashMap<>());

        // When
        boolean hasConfig = configService.hasCustomConfig(apiKey);

        // Then
        assertThat(hasConfig).isFalse();
    }

    @Test
    void deleteConfig_shouldDeleteRedisKey() {
        // Given
        String apiKey = "key-to-delete";

        // When
        configService.deleteConfig(apiKey);

        // Then
        verify(redisTemplate).delete(RateLimitConfigService.CONFIG_KEY_PREFIX + apiKey);
    }
}
