package com.ratelimiter.service;

import com.ratelimiter.config.RateLimiterProperties;
import com.ratelimiter.model.RateLimitConfig;
import com.ratelimiter.model.RateLimitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the core RateLimiterService.
 * Covers allowed requests, rate limit exceeded, status, and reset scenarios.
 */
@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private DefaultRedisScript<List> slidingWindowScript;

    @Mock
    private RateLimitConfigService configService;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private RateLimiterService rateLimiterService;

    private static final String API_KEY = "test-api-key";
    private static final String REDIS_KEY = "rate_limit:" + API_KEY;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService(redisTemplate, slidingWindowScript, configService);
    }

    @Test
    void checkRateLimit_whenRequestAllowed_shouldReturnAllowedResult() {
        // Given
        RateLimitConfig config = new RateLimitConfig(API_KEY, 10, 60);
        when(configService.getConfig(API_KEY)).thenReturn(config);

        long nowMs = System.currentTimeMillis();
        long resetMs = nowMs + 60_000;
        // Script returns: [allowed=1, count=1, limit=10, remaining=9, reset=...]
        List<Long> scriptResult = Arrays.asList(1L, 1L, 10L, 9L, resetMs);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any()))
                .thenReturn(scriptResult);

        // When
        RateLimitResult result = rateLimiterService.checkRateLimit(API_KEY);

        // Then
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getCurrentCount()).isEqualTo(1L);
        assertThat(result.getLimit()).isEqualTo(10L);
        assertThat(result.getRemaining()).isEqualTo(9L);
        assertThat(result.getApiKey()).isEqualTo(API_KEY);
    }

    @Test
    void checkRateLimit_whenLimitExceeded_shouldReturnDeniedResult() {
        // Given
        RateLimitConfig config = new RateLimitConfig(API_KEY, 10, 60);
        when(configService.getConfig(API_KEY)).thenReturn(config);

        long nowMs = System.currentTimeMillis();
        long resetMs = nowMs + 30_000;
        // Script returns: [allowed=0, count=10, limit=10, remaining=0, reset=...]
        List<Long> scriptResult = Arrays.asList(0L, 10L, 10L, 0L, resetMs);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any()))
                .thenReturn(scriptResult);

        // When
        RateLimitResult result = rateLimiterService.checkRateLimit(API_KEY);

        // Then
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getCurrentCount()).isEqualTo(10L);
        assertThat(result.getLimit()).isEqualTo(10L);
        assertThat(result.getRemaining()).isEqualTo(0L);
    }

    @Test
    void checkRateLimit_whenRedisUnavailable_shouldFailOpen() {
        // Given - Redis throws exception
        RateLimitConfig config = new RateLimitConfig(API_KEY, 10, 60);
        when(configService.getConfig(API_KEY)).thenReturn(config);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any()))
                .thenThrow(new RuntimeException("Connection refused"));

        // When
        RateLimitResult result = rateLimiterService.checkRateLimit(API_KEY);

        // Then - should fail open (allow the request)
        assertThat(result.isAllowed()).isTrue();
    }

    @Test
    void getStatus_shouldReturnCurrentWindowState() {
        // Given
        RateLimitConfig config = new RateLimitConfig(API_KEY, 10, 60);
        when(configService.getConfig(API_KEY)).thenReturn(config);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        // Simulate 3 requests in the window
        doNothing().when(zSetOperations)
                   .removeRangeByScore(anyString(), anyDouble(), anyDouble());
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(3L);

        long oldestScore = System.currentTimeMillis() - 30_000;
        ZSetOperations.TypedTuple<String> tuple = new DefaultTypedTuple<>("entry1", (double) oldestScore);
        when(zSetOperations.rangeWithScores(anyString(), eq(0L), eq(0L)))
                .thenReturn(new LinkedHashSet<>(Collections.singletonList(tuple)));

        // When
        RateLimitResult result = rateLimiterService.getStatus(API_KEY);

        // Then
        assertThat(result.getCurrentCount()).isEqualTo(3L);
        assertThat(result.getLimit()).isEqualTo(10L);
        assertThat(result.getRemaining()).isEqualTo(7L);
        assertThat(result.isAllowed()).isTrue();
    }

    @Test
    void getStatus_whenNoRequests_shouldShowFullRemaining() {
        // Given
        RateLimitConfig config = new RateLimitConfig(API_KEY, 10, 60);
        when(configService.getConfig(API_KEY)).thenReturn(config);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        doNothing().when(zSetOperations)
                   .removeRangeByScore(anyString(), anyDouble(), anyDouble());
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(0L);
        when(zSetOperations.rangeWithScores(anyString(), eq(0L), eq(0L)))
                .thenReturn(new LinkedHashSet<>());

        // When
        RateLimitResult result = rateLimiterService.getStatus(API_KEY);

        // Then
        assertThat(result.getCurrentCount()).isEqualTo(0L);
        assertThat(result.getRemaining()).isEqualTo(10L);
        assertThat(result.isAllowed()).isTrue();
    }

    @Test
    void resetRateLimit_whenKeyExists_shouldDeleteKeyAndReturnTrue() {
        // Given
        when(redisTemplate.delete(REDIS_KEY)).thenReturn(true);
        when(redisTemplate.delete(REDIS_KEY + ":seq")).thenReturn(true);

        // When
        boolean result = rateLimiterService.resetRateLimit(API_KEY);

        // Then
        assertThat(result).isTrue();
        verify(redisTemplate).delete(REDIS_KEY);
        verify(redisTemplate).delete(REDIS_KEY + ":seq");
    }

    @Test
    void resetRateLimit_whenKeyNotExists_shouldReturnFalse() {
        // Given - key does not exist
        when(redisTemplate.delete(REDIS_KEY)).thenReturn(false);
        when(redisTemplate.delete(REDIS_KEY + ":seq")).thenReturn(false);

        // When
        boolean result = rateLimiterService.resetRateLimit(API_KEY);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void checkRateLimit_usesCorrectRedisKey() {
        // Given
        RateLimitConfig config = new RateLimitConfig(API_KEY, 5, 30);
        when(configService.getConfig(API_KEY)).thenReturn(config);

        long nowMs = System.currentTimeMillis();
        List<Long> scriptResult = Arrays.asList(1L, 1L, 5L, 4L, nowMs + 30_000);
        when(redisTemplate.execute(any(), anyList(), any(), any(), any()))
                .thenReturn(scriptResult);

        // When
        rateLimiterService.checkRateLimit(API_KEY);

        // Then - verify the Lua script was called with the correct key
        verify(redisTemplate).execute(
                any(),
                eq(Collections.singletonList(REDIS_KEY)),
                any(), any(), any()
        );
    }
}
