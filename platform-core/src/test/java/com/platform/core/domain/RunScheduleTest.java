package com.platform.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RunScheduleTest {

  @Test
  void markDispatchedAdvancesNextRunByInterval() {
    RunSchedule s =
        new RunSchedule(
            UUID.randomUUID(), "Nightly", "acme/app", 42L, "main", null, 60, true, null);
    Instant when = Instant.parse("2026-07-04T10:00:00Z");

    s.markDispatched(when);

    assertThat(s.getLastDispatchedAt()).isEqualTo(when);
    assertThat(s.getNextRunAt()).isEqualTo(when.plus(60, ChronoUnit.MINUTES));
  }

  @Test
  void blankRefDefaultsToMain() {
    RunSchedule s =
        new RunSchedule(UUID.randomUUID(), "S", "acme/app", 1L, "  ", null, 30, true, null);
    assertThat(s.getRef()).isEqualTo("main");
  }
}
