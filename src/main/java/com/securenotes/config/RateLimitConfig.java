package com.securenotes.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter configuration using Bucket4j.
 * Implements per-IP rate limiting for login endpoints to prevent brute force attacks.
 */
@Component
public class RateLimitConfig {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private static final int LOGIN_REQUESTS_PER_MINUTE = 5;
    private static final int GENERAL_REQUESTS_PER_MINUTE = 100;

    /**
     * Gets or creates a rate limit bucket for login attempts.
     * Allows 5 login attempts per minute per IP.
     */
    public Bucket resolveBucketForLogin(String key) {
        return buckets.computeIfAbsent(key, this::createLoginBucket);
    }

    /**
     * Gets or creates a general rate limit bucket.
     * Allows 100 requests per minute per IP.
     */
    public Bucket resolveBucketForGeneral(String key) {
        return buckets.computeIfAbsent("general:" + key, this::createGeneralBucket);
    }

    private Bucket createLoginBucket(String key) {
        Bandwidth limit = Bandwidth.classic(
                LOGIN_REQUESTS_PER_MINUTE,
                Refill.intervally(LOGIN_REQUESTS_PER_MINUTE, Duration.ofMinutes(1))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createGeneralBucket(String key) {
        Bandwidth limit = Bandwidth.classic(
                GENERAL_REQUESTS_PER_MINUTE,
                Refill.intervally(GENERAL_REQUESTS_PER_MINUTE, Duration.ofMinutes(1))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Clears all rate limit buckets (useful for testing).
     */
    public void clearBuckets() {
        buckets.clear();
    }
}
