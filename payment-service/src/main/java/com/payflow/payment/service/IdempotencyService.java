package com.payflow.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.payment.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Idempotency Service — Prevents duplicate payment processing.
 * 
 * Uses Redis to store idempotency keys with 24-hour TTL.
 * If same key comes in again, returns the cached response.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String KEY_PREFIX = "idempotency:";
    private static final Duration TTL = Duration.ofHours(24);

    /**
     * Check if this idempotency key already has a cached response.
     */
    public PaymentResponse getCachedResponse(String idempotencyKey) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + idempotencyKey);
        if (json == null) return null;

        try {
            return objectMapper.readValue(json, PaymentResponse.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse cached idempotency response: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Store the response for this idempotency key (24 hour TTL).
     */
    public void cacheResponse(String idempotencyKey, PaymentResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(KEY_PREFIX + idempotencyKey, json, TTL);
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache idempotency response: {}", e.getMessage());
        }
    }
}
