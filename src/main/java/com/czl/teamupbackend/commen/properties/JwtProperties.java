package com.czl.teamupbackend.commen.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT configuration properties.
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * JWT signing key, at least 32 chars for HS256.
     */
    private String secret = "TeamUpJwtSecretKeyAtLeast32Chars!";

    /**
     * Token validity period (milliseconds).
     */
    private long expireMs = 24 * 60 * 60 * 1000L;

    /**
     * Issuer name.
     */
    private String issuer = "teamup-backend";
}
