package com.ratelimiter.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.Map;

/**
 * Global exception handler for the Rate Limiter application.
 * Provides consistent error responses across all controllers.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles all unexpected exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex, WebRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Internal Server Error",
                "message", ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred",
                "timestamp", Instant.now().toString(),
                "path", request.getDescription(false)
        ));
    }

    /**
     * Handles IllegalArgumentException for invalid inputs.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {
        log.warn("Invalid argument: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "Bad Request",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString(),
                "path", request.getDescription(false)
        ));
    }

    /**
     * Handles Redis connection errors gracefully.
     */
    @ExceptionHandler(org.springframework.data.redis.RedisConnectionFailureException.class)
    public ResponseEntity<Map<String, Object>> handleRedisConnectionError(
            org.springframework.data.redis.RedisConnectionFailureException ex,
            WebRequest request) {
        log.error("Redis connection failure: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", "Service Unavailable",
                "message", "Rate limiting service is temporarily unavailable. Please try again.",
                "timestamp", Instant.now().toString(),
                "path", request.getDescription(false)
        ));
    }
}
