package com.platform.ingestion.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.core.domain.QualityGate;
import com.platform.core.domain.SotRelease;
import com.platform.core.quality.QualityGateService;
import com.platform.core.repository.SotReleaseRepository;
import com.platform.ingestion.quality.ReleaseGateMetricsService.ReleaseGateMetrics;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReleaseGateServiceTest {

  @Mock QualityGateService gateConfig;
  @Mock ReleaseGateMetricsService metrics;
  @Mock SotReleaseRepository releaseRepo;

  ReleaseGateService service;

  private final UUID pid = UUID.randomUUID();
  private final UUID releaseId = UUID.randomUUID();
  private SotRelease release;

  @BeforeEach
  void setUp() {
    service = new ReleaseGateService(gateConfig, metrics, releaseRepo);
    release = new SotRelease(pid, "v1.0", "VERSION", null);
    lenient().when(releaseRepo.findById(releaseId)).thenReturn(Optional.of(release));
    lenient().when(releaseRepo.save(any(SotRelease.class))).thenAnswer(i -> i.getArgument(0));
  }

  private QualityGate defaultGate() {
    return new QualityGate(pid); // enabled, 0.90 pass-rate, 0 coverage/blockers/flaky
  }

  private ReleaseGateMetrics metrics(double passRate, int openBlockers, int criticalFlaky) {
    return new ReleaseGateMetrics(passRate, 100.0, openBlockers, criticalFlaky, true, 10);
  }

  @Test
  void allCriteriaWithinThresholdsPass() {
    when(gateConfig.get(pid)).thenReturn(defaultGate());
    when(metrics.forRelease(pid, releaseId)).thenReturn(metrics(1.0, 0, 0));

    var result = service.evaluate(pid, releaseId);

    assertThat(result.status()).isEqualTo("PASS");
    assertThat(result.criteria()).allMatch(ReleaseGateService.GateCriterion::passed);
    assertThat(release.getGateStatus()).isEqualTo("PASS");
    verify(releaseRepo).save(release);
  }

  @Test
  void aSubThresholdMetricFailsAndFlagsThatCriterion() {
    when(gateConfig.get(pid)).thenReturn(defaultGate());
    when(metrics.forRelease(pid, releaseId))
        .thenReturn(metrics(0.50, 3, 0)); // pass-rate + blockers bad

    var result = service.evaluate(pid, releaseId);

    assertThat(result.status()).isEqualTo("FAIL");
    assertThat(release.getGateStatus()).isEqualTo("FAIL");
    assertThat(result.criteria())
        .filteredOn(c -> c.name().equals("Pass rate"))
        .allMatch(c -> !c.passed());
    assertThat(result.criteria())
        .filteredOn(c -> c.name().equals("Open blockers"))
        .allMatch(c -> !c.passed());
  }

  @Test
  void overrideRequiresAReason() {
    assertThat(catchThrowable(() -> service.override(pid, releaseId, "  ", "qa-lead")))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void overrideRecordsReasonAndActorAndSetsOverridden() {
    var out = service.override(pid, releaseId, "blocker patched in hotfix", "qa-lead");

    assertThat(out.getGateStatus()).isEqualTo("OVERRIDDEN");
    assertThat(out.getGateOverrideReason()).isEqualTo("blocker patched in hotfix");
    assertThat(out.getGateOverriddenBy()).isEqualTo("qa-lead");
    verify(releaseRepo).save(release);
  }

  @Test
  void disabledGateAlwaysPassesWithoutComputingMetrics() {
    QualityGate disabled = new QualityGate(pid);
    disabled.update(false, 0.99, 100, 0, 0, true);
    when(gateConfig.get(pid)).thenReturn(disabled);

    var result = service.evaluate(pid, releaseId);

    assertThat(result.status()).isEqualTo("PASS");
    assertThat(result.enabled()).isFalse();
    assertThat(result.criteria()).isEmpty();
    assertThat(release.getGateStatus()).isEqualTo("PASS");
    verify(metrics, never()).forRelease(any(), any());
  }
}
