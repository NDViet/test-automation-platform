package com.platform.ingestion.quality;

import com.platform.core.domain.QualityGate;
import com.platform.core.domain.SotRelease;
import com.platform.core.quality.QualityGateService;
import com.platform.core.repository.SotReleaseRepository;
import com.platform.ingestion.quality.ReleaseGateMetricsService.ReleaseGateMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Evaluates a release against its project's quality gate and persists the verdict on the release. A
 * disabled gate always passes (no criteria). The result is per-criterion so the UI can show exactly
 * which threshold blocked a release.
 */
@Service
public class ReleaseGateService {

  private final QualityGateService gateConfig;
  private final ReleaseGateMetricsService metrics;
  private final SotReleaseRepository releaseRepo;

  public ReleaseGateService(
      QualityGateService gateConfig,
      ReleaseGateMetricsService metrics,
      SotReleaseRepository releaseRepo) {
    this.gateConfig = gateConfig;
    this.metrics = metrics;
    this.releaseRepo = releaseRepo;
  }

  /** One threshold check: what was measured vs what was required, and whether it passed. */
  public record GateCriterion(String name, String actual, String threshold, boolean passed) {}

  /** The overall verdict plus the per-criterion breakdown. */
  public record GateEvaluation(String status, boolean enabled, List<GateCriterion> criteria) {}

  @Transactional
  public GateEvaluation evaluate(UUID projectId, UUID releaseId) {
    SotRelease release = loadRelease(projectId, releaseId);
    QualityGate cfg = gateConfig.get(projectId);

    if (!cfg.isEnabled()) {
      release.recordGateEvaluation("PASS");
      releaseRepo.save(release);
      return new GateEvaluation("PASS", false, List.of());
    }

    ReleaseGateMetrics m = metrics.forRelease(projectId, releaseId);
    List<GateCriterion> criteria = new ArrayList<>();
    criteria.add(
        new GateCriterion(
            "Pass rate",
            pct(m.passRate()),
            ">= " + pct(cfg.getMinPassRate()),
            m.passRate() >= cfg.getMinPassRate()));
    criteria.add(
        new GateCriterion(
            "Coverage",
            num(m.coveragePct()) + "%",
            ">= " + num(cfg.getMinCoveragePct()) + "%",
            m.coveragePct() >= cfg.getMinCoveragePct()));
    criteria.add(
        new GateCriterion(
            "Open blockers",
            Integer.toString(m.openBlockers()),
            "<= " + cfg.getMaxOpenBlockers(),
            m.openBlockers() <= cfg.getMaxOpenBlockers()));
    criteria.add(
        new GateCriterion(
            "Critical-flaky tests",
            Integer.toString(m.criticalFlaky()),
            "<= " + cfg.getMaxCriticalFlaky(),
            m.criticalFlaky() <= cfg.getMaxCriticalFlaky()));
    if (cfg.isRequireSuitesExecuted()) {
      criteria.add(
          new GateCriterion(
              "Suites executed",
              m.suitesExecuted() ? "yes" : "no",
              "required",
              m.suitesExecuted()));
    }

    boolean passed = criteria.stream().allMatch(GateCriterion::passed);
    String status = passed ? "PASS" : "FAIL";
    release.recordGateEvaluation(status);
    releaseRepo.save(release);
    return new GateEvaluation(status, true, criteria);
  }

  /**
   * Overrides the gate with a recorded reason so a release can ship despite a FAIL. The reason is
   * mandatory (an override with no justification is not an override), and the actor is stamped for
   * the audit trail.
   */
  @Transactional
  public SotRelease override(UUID projectId, UUID releaseId, String reason, String actor) {
    if (reason == null || reason.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An override reason is required.");
    }
    SotRelease release = loadRelease(projectId, releaseId);
    release.recordGateOverride(reason.trim(), actor);
    return releaseRepo.save(release);
  }

  private SotRelease loadRelease(UUID projectId, UUID releaseId) {
    return releaseRepo
        .findById(releaseId)
        .filter(r -> projectId.equals(r.getProjectId()))
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Release not found: " + releaseId));
  }

  private static String pct(double fraction) {
    return String.format(Locale.US, "%.1f%%", fraction * 100);
  }

  private static String num(double v) {
    return String.format(Locale.US, "%.1f", v);
  }
}
