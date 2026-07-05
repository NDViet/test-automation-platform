package com.platform.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class FailureClusterTest {

  @Test
  void newClusterDefaultsToOpenWithZeroOccurrences() {
    FailureCluster c =
        new FailureCluster(
            UUID.randomUUID(),
            "AUTOMATED",
            "abc123",
            "acme.LoginTest#login",
            "NPE in login",
            "java.lang.NullPointerException");

    assertThat(c.getStatus()).isEqualTo("OPEN");
    assertThat(c.getOccurrences()).isZero();
    assertThat(c.getSource()).isEqualTo("AUTOMATED");
    assertThat(c.getSignatureHash()).isEqualTo("abc123");
    assertThat(c.getFirstSeenAt()).isNotNull();

    c.setStatus("KNOWN_ISSUE");
    c.setOccurrences(3);
    assertThat(c.getStatus()).isEqualTo("KNOWN_ISSUE");
    assertThat(c.getOccurrences()).isEqualTo(3);
  }
}
