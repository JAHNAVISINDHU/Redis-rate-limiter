package com.ratelimiter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that the Spring Boot application context loads correctly with Redis.
 */
@SpringBootTest
@Testcontainers
class RedisRateLimiterApplicationTest {

    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }

    @Test
    void mainApplicationBeansArePresent() {
        assertThat(applicationContext.containsBean("rateLimiterService")).isTrue();
        assertThat(applicationContext.containsBean("rateLimitConfigService")).isTrue();
        assertThat(applicationContext.containsBean("redisTemplate")).isTrue();
        assertThat(applicationContext.containsBean("slidingWindowScript")).isTrue();
    }
}
