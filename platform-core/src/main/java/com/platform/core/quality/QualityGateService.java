package com.platform.core.quality;

import com.platform.core.domain.QualityGate;
import com.platform.core.repository.QualityGateRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and upserts a project's quality-gate thresholds. When a project has never configured a
 * gate, {@link #get} returns a transient defaults instance (enabled, 90% pass-rate) so evaluation
 * has something to compare against without forcing every project to set one up first.
 */
@Service
public class QualityGateService {

  private final QualityGateRepository repo;

  public QualityGateService(QualityGateRepository repo) {
    this.repo = repo;
  }

  /** The project's saved gate, or a transient defaults instance if none is configured. */
  @Transactional(readOnly = true)
  public QualityGate get(UUID projectId) {
    return repo.findByProjectId(projectId).orElseGet(() -> new QualityGate(projectId));
  }

  @Transactional
  public QualityGate upsert(
      UUID projectId,
      boolean enabled,
      double minPassRate,
      double minCoveragePct,
      int maxOpenBlockers,
      int maxCriticalFlaky,
      boolean requireSuitesExecuted) {
    QualityGate g = repo.findByProjectId(projectId).orElseGet(() -> new QualityGate(projectId));
    g.update(
        enabled,
        minPassRate,
        minCoveragePct,
        maxOpenBlockers,
        maxCriticalFlaky,
        requireSuitesExecuted);
    return repo.save(g);
  }
}
