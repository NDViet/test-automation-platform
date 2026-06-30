package com.platform.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Issues and verifies HS256 JWTs. Signed with {@code PLATFORM_JWT_SECRET} (any length — hashed to a
 * 256-bit key); if unset, a random per-boot key is used so dev sessions don't survive a restart
 * (never run prod without the secret). The token carries identity only — roles are resolved fresh
 * per request elsewhere.
 */
@Component
public class JwtService {

  private static final Logger log = LoggerFactory.getLogger(JwtService.class);

  private final SecretKey key;
  private final long ttlSeconds;

  public JwtService(
      @Value("${PLATFORM_JWT_SECRET:}") String secret,
      @Value("${platform.security.token-ttl-seconds:28800}") long ttlSeconds) {
    this.ttlSeconds = ttlSeconds;
    this.key = deriveKey(secret);
  }

  private static SecretKey deriveKey(String secret) {
    byte[] material;
    if (secret == null || secret.isBlank()) {
      material = new byte[32];
      new SecureRandom().nextBytes(material);
      log.warn(
          "PLATFORM_JWT_SECRET not set — using a random per-boot key; sessions reset on restart."
              + " Set PLATFORM_JWT_SECRET in production.");
    } else {
      material = sha256(secret);
    }
    return Keys.hmacShaKeyFor(material);
  }

  /** Issue a signed token for the user. */
  public String issue(UUID userId, String username, boolean superAdmin) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(userId.toString())
        .claim("username", username)
        .claim("super", superAdmin)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(ttlSeconds)))
        .signWith(key)
        .compact();
  }

  /** Verify a token and return the principal; throws {@code JwtException} on invalid/expired. */
  public AuthenticatedUser verify(String token) {
    Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    return new AuthenticatedUser(
        UUID.fromString(c.getSubject()),
        c.get("username", String.class),
        Boolean.TRUE.equals(c.get("super", Boolean.class)));
  }

  public long ttlSeconds() {
    return ttlSeconds;
  }

  private static byte[] sha256(String s) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
