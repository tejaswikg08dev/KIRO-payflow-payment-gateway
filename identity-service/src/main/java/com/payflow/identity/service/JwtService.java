package com.payflow.identity.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT Service — Creates and validates JSON Web Tokens.
 *
 * Access Token: Short-lived (15 min), used for API authentication.
 * Refresh Token: Long-lived (7 days), used to get new access tokens.
 */
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpiry;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    /**
     * Generate an access token for a user.
     */
    public String generateAccessToken(String userId, String email, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("email", email);
        claims.put("role", role);
        claims.put("type", "ACCESS");

        return buildToken(claims, email, accessTokenExpiry);
    }

    /**
     * Generate a refresh token for a user.
     */
    public String generateRefreshToken(String userId, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("type", "REFRESH");

        return buildToken(claims, email, refreshTokenExpiry);
    }

    /**
     * Validate a token and extract claims.
     * Returns null if token is invalid or expired.
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extract user ID from a valid token.
     */
    public String extractUserId(String token) {
        Claims claims = validateToken(token);
        return claims != null ? claims.get("userId", String.class) : null;
    }

    /**
     * Check if token is expired.
     */
    public boolean isTokenExpired(String token) {
        Claims claims = validateToken(token);
        return claims == null || claims.getExpiration().before(new Date());
    }

    private String buildToken(Map<String, Object> claims, String subject, long expiry) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiry))
                .signWith(getSigningKey())
                .compact();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
