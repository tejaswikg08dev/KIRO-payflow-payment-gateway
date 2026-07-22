package com.payflow.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Rate Limiting Filter — Limits requests per API key to prevent abuse.
 * 
 * How it works:
 * 1. Extract API key from X-Api-Key header (or use IP if no key)
 * 2. Check Redis counter for this key: "rate:{api_key}" → current count
 * 3. If count < 100 → allow request, increment counter
 * 4. If count >= 100 → reject with 429 Too Many Requests
 * 5. Counter expires after 1 minute (sliding window)
 * 
 * Rate: 100 requests per minute per API key.
 * This prevents a single merchant from overwhelming our system.
 */
@Slf4j
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private static final int MAX_REQUESTS_PER_MINUTE = 100;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    public RateLimitFilter(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Skip rate limiting for health checks and Eureka
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/actuator") || path.startsWith("/eureka")) {
            return chain.filter(exchange);
        }

        // Get identifier (API key or IP address)
        String apiKey = exchange.getRequest().getHeaders().getFirst("X-Api-Key");
        String identifier = (apiKey != null) ? apiKey : getClientIp(exchange);
        String redisKey = "rate:" + identifier;

        // Increment counter in Redis and check limit
        return redisTemplate.opsForValue().increment(redisKey)
                .flatMap(count -> {
                    if (count == 1) {
                        // First request — set expiry on the key
                        return redisTemplate.expire(redisKey, WINDOW)
                                .then(chain.filter(exchange));
                    } else if (count <= MAX_REQUESTS_PER_MINUTE) {
                        // Within limit — allow
                        return chain.filter(exchange);
                    } else {
                        // Over limit — reject with 429
                        log.warn("Rate limit exceeded for: {}", identifier);
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete();
                    }
                });
    }

    private String getClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null) {
            return forwarded.split(",")[0].trim();
        }
        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    @Override
    public int getOrder() {
        return -1; // Run BEFORE routing (high priority)
    }
}
