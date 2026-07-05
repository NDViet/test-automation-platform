package com.platform.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationPrefTest {

  @Test
  void updateReplacesFields() {
    NotificationPref p =
        new NotificationPref(
            UUID.randomUUID(), "SLACK", "https://hook", "[\"RUN_COMPLETED\"]", true);

    p.update("EMAIL", "qa@x.io", "[\"GATE_RESULT\"]", false);

    assertThat(p.getChannel()).isEqualTo("EMAIL");
    assertThat(p.getDestination()).isEqualTo("qa@x.io");
    assertThat(p.getEvents()).isEqualTo("[\"GATE_RESULT\"]");
    assertThat(p.isEnabled()).isFalse();
  }
}
