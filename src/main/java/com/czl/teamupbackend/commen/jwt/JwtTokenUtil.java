package com.czl.teamupbackend.commen.jwt;

import com.czl.teamupbackend.commen.context.UserContext;
import com.czl.teamupbackend.commen.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * JWT utility for token generation and validation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenUtil {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_USERNAME = "username";

    private final JwtProperties jwtProperties;

    /**
     * Generate token with minimal user context.
     */
    public String generateToken(Long userId, String username) {
        Instant now = Instant.now();
        Instant expiration = now.plusMillis(jwtProperties.getExpireMs());

        return Jwts.builder()
            .subject(String.valueOf(userId))
            .issuer(jwtProperties.getIssuer())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .claims(Map.of(
                CLAIM_USER_ID, userId,
                CLAIM_USERNAME, username == null ? "" : username
            ))
            .signWith(getSignKey())
            .compact();
    }

    /**
     * Parse token and return claims.
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(getSignKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    /**
     * Validate token and bind user info to UserContext.
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            Long userId = parseUserId(claims);
            String username = claims.get(CLAIM_USERNAME, String.class);
            UserContext.setCurrentUser(userId, username);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
        } catch (UnsupportedJwtException | MalformedJwtException | SecurityException
                 | IllegalArgumentException e) {
            log.warn("JWT token invalid: {}", e.getMessage());
        } catch (Exception e) {
            log.error("JWT token validation failed unexpectedly", e);
        }
        return false;
    }

    /**
     * Extract user id from valid token.
     */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        return parseUserId(claims);
    }

    /**
     * Extract username from valid token.
     */
    public String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims.get(CLAIM_USERNAME, String.class);
    }

    private Long parseUserId(Claims claims) {
        Object userIdObj = claims.get(CLAIM_USER_ID);
        if (userIdObj == null) {
            throw new IllegalArgumentException("Missing userId claim in JWT");
        }
        if (userIdObj instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(userIdObj));
    }

    private SecretKey getSignKey() {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must not be empty");
        }
        byte[] keyBytes = resolveSecretBytes(secret);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                "JWT secret is too short. Use at least 32 bytes for HS256. "
                    + "Plain text example: 32+ chars. Base64 example: prefix with 'base64:'."
            );
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private byte[] resolveSecretBytes(String secret) {
        String normalized = secret.trim();
        if (normalized.regionMatches(true, 0, "base64:", 0, "base64:".length())) {
            String base64Part = normalized.substring("base64:".length()).trim();
            if (base64Part.isEmpty()) {
                throw new IllegalArgumentException("JWT base64 secret must not be empty");
            }
            return Decoders.BASE64.decode(base64Part);
        }
        return normalized.getBytes(StandardCharsets.UTF_8);
    }
}
