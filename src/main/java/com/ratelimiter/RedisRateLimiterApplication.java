package com.ratelimiter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Redis Rate Limiter application.
 * Implements a distributed rate limiting service using the Sliding Window Log
 * algorithm backed by Redis Sorted Sets and atomic Lua scripting.
 */
@SpringBootApplication
public class RedisRateLimiterApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedisRateLimiterApplication.class, args);
    }
}
