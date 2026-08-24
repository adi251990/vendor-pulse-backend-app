package com.vendorpulse.platform.config;

import com.vendorpulse.platform.identity.entity.User;
import com.vendorpulse.platform.identity.entity.UserRole;
import com.vendorpulse.platform.identity.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and verifies the access tokens consumed by the Android client.
 * Access tokens are short-lived (15 min default); the Android app is
 * expected to hold a separate opaque refresh token (persisted hashed
 * server-side, not implemented as a JWT) for silent refresh - see
 * AuthService#refresh.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.getAccessTokenTtlMinutes() * 60);

        var builder = Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("tokenVersion", user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry));

        if (user.getOrgId() != null) {
            builder.claim("orgId", user.getOrgId().toString());
        }

        return builder.signWith(key).compact();
    }

    public UserPrincipal parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token)
                    .getPayload();

            UUID userId = UUID.fromString(claims.getSubject());
            UserRole role = UserRole.valueOf(claims.get("role", String.class));
            String orgIdClaim = claims.get("orgId", String.class);
            UUID orgId = orgIdClaim != null ? UUID.fromString(orgIdClaim) : null;

            return new UserPrincipal(userId, orgId, role);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Invalid or expired token", e);
        }
    }

    public int extractTokenVersion(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        Integer version = claims.get("tokenVersion", Integer.class);
        return version == null ? 0 : version;
    }

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
