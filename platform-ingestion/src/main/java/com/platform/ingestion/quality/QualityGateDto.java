package com.platform.ingestion.quality;

import com.platform.core.domain.QualityGate;

/** A project's quality-gate thresholds (read + write payload). */
public record QualityGateDto(
    boolean enabled,
    double minPassRate,
    double minCoveragePct,
    int maxOpenBlockers,
    int maxCriticalFlaky,
    boolean requireSuitesExecuted) {

  public static QualityGateDto from(QualityGate g) {
    return new QualityGateDto(
        g.isEnabled(),
        g.getMinPassRate(),
        g.getMinCoveragePct(),
        g.getMaxOpenBlockers(),
        g.getMaxCriticalFlaky(),
        g.isRequireSuitesExecuted());
  }
}
