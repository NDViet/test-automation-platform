package com.platform.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class QualityGateTest {

  @Test
  void newGateHasSensibleDefaults() {
    QualityGate g = new QualityGate(UUID.randomUUID());

    assertThat(g.isEnabled()).isTrue();
    assertThat(g.getMinPassRate()).isEqualTo(0.90);
    assertThat(g.getMinCoveragePct()).isEqualTo(0);
    assertThat(g.getMaxOpenBlockers()).isZero();
    assertThat(g.getMaxCriticalFlaky()).isZero();
    assertThat(g.isRequireSuitesExecuted()).isFalse();
  }

  @Test
  void updateReplacesAllThresholds() {
    QualityGate g = new QualityGate(UUID.randomUUID());

    g.update(false, 0.80, 75, 2, 1, true);

    assertThat(g.isEnabled()).isFalse();
    assertThat(g.getMinPassRate()).isEqualTo(0.80);
    assertThat(g.getMinCoveragePct()).isEqualTo(75);
    assertThat(g.getMaxOpenBlockers()).isEqualTo(2);
    assertThat(g.getMaxCriticalFlaky()).isEqualTo(1);
    assertThat(g.isRequireSuitesExecuted()).isTrue();
  }

  @Test
  void releaseRecordsGateVerdictAndOverride() {
    SotRelease r = new SotRelease(UUID.randomUUID(), "v1.0", "VERSION", null);

    r.recordGateEvaluation("FAIL");
    assertThat(r.getGateStatus()).isEqualTo("FAIL");
    assertThat(r.getGateEvaluatedAt()).isNotNull();
    assertThat(r.getGateOverrideReason()).isNull();

    r.recordGateOverride("Hotfix — blocker already patched in prod", "qa-lead");
    assertThat(r.getGateStatus()).isEqualTo("OVERRIDDEN");
    assertThat(r.getGateOverrideReason()).isEqualTo("Hotfix — blocker already patched in prod");
    assertThat(r.getGateOverriddenBy()).isEqualTo("qa-lead");
  }
}
