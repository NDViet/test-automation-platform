package com.platform.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.JwtException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private final JwtService svc = new JwtService("a-test-signing-secret", 3600);

  @Test
  void issueAndVerifyRoundTrip() {
    UUID id = UUID.randomUUID();
    AuthenticatedUser u = svc.verify(svc.issue(id, "alice", true));
    assertThat(u.userId()).isEqualTo(id);
    assertThat(u.username()).isEqualTo("alice");
    assertThat(u.superAdmin()).isTrue();
  }

  @Test
  void tamperedTokenRejected() {
    String t = svc.issue(UUID.randomUUID(), "a", false);
    String tampered = t.substring(0, t.length() - 2) + (t.endsWith("a") ? "bb" : "aa");
    assertThatThrownBy(() -> svc.verify(tampered)).isInstanceOf(JwtException.class);
  }

  @Test
  void wrongSecretRejected() {
    String t = svc.issue(UUID.randomUUID(), "a", false);
    JwtService other = new JwtService("a-different-secret", 3600);
    assertThatThrownBy(() -> other.verify(t)).isInstanceOf(JwtException.class);
  }

  @Test
  void expiredTokenRejected() {
    JwtService expired = new JwtService("a-test-signing-secret", -10); // already past
    String t = expired.issue(UUID.randomUUID(), "a", false);
    assertThatThrownBy(() -> expired.verify(t)).isInstanceOf(JwtException.class);
  }
}
