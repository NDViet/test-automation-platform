package com.platform.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiGenerationRunRegenTest {

  private AiGenerationRun newRun() {
    return new AiGenerationRun(
        UUID.randomUUID(), UUID.randomUUID(), "[]", "original", null, null, null, 3);
  }

  @Test
  void recordRegenInputsSetsFieldsAndBumpsRound() {
    AiGenerationRun run = newRun();
    assertThat(run.getRegenRound()).isZero();

    run.recordRegenInputs("[\"req-1\",\"req-2\"]", "more context", "[{\"pr\":42}]");

    assertThat(run.getRequirementIdsJson()).isEqualTo("[\"req-1\",\"req-2\"]");
    assertThat(run.getFreeText()).isEqualTo("more context");
    assertThat(run.getPrContextJson()).isEqualTo("[{\"pr\":42}]");
    assertThat(run.getRegenRound()).isEqualTo(1);
  }

  @Test
  void eachRegenIncrementsRound() {
    AiGenerationRun run = newRun();

    run.recordRegenInputs("[\"a\"]", "first", null);
    run.recordRegenInputs("[\"b\"]", "second", "[]");

    assertThat(run.getRegenRound()).isEqualTo(2);
    assertThat(run.getFreeText()).isEqualTo("second");
    assertThat(run.getRequirementIdsJson()).isEqualTo("[\"b\"]");
  }
}
