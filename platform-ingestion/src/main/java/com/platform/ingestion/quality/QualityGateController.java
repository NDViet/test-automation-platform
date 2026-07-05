package com.platform.ingestion.quality;

import com.platform.core.quality.QualityGateService;
import com.platform.ingestion.audit.AuditLog;
import com.platform.security.authz.Capability;
import com.platform.security.web.CurrentUser;
import com.platform.security.web.RequireCapability;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Release quality-gate configuration, evaluation, and the audited override. Reads require VIEW;
 * overriding a failed gate is a policy decision, so it requires MANAGE_PROJECT and is audited with
 * the justifying reason.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
@RequireCapability(value = Capability.VIEW_RESULTS, scope = "projectId")
@Tag(name = "Quality Gate")
public class QualityGateController {

  private final ReleaseGateService gate;
  private final QualityGateService config;

  public QualityGateController(ReleaseGateService gate, QualityGateService config) {
    this.gate = gate;
    this.config = config;
  }

  @GetMapping("/quality-gate")
  public QualityGateDto getConfig(@PathVariable UUID projectId) {
    return QualityGateDto.from(config.get(projectId));
  }

  @PutMapping("/quality-gate")
  @RequireCapability(value = Capability.MANAGE_PROJECT, scope = "projectId")
  public QualityGateDto updateConfig(
      @PathVariable UUID projectId, @RequestBody QualityGateDto req) {
    return QualityGateDto.from(
        config.upsert(
            projectId,
            req.enabled(),
            req.minPassRate(),
            req.minCoveragePct(),
            req.maxOpenBlockers(),
            req.maxCriticalFlaky(),
            req.requireSuitesExecuted()));
  }

  @PostMapping("/releases/{releaseId}/evaluate-gate")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public ReleaseGateService.GateEvaluation evaluateGate(
      @PathVariable UUID projectId, @PathVariable UUID releaseId) {
    return gate.evaluate(projectId, releaseId);
  }

  @PostMapping("/releases/{releaseId}/override-gate")
  @RequireCapability(value = Capability.MANAGE_PROJECT, scope = "projectId")
  @AuditLog(eventType = "GATE_OVERRIDE", resourceType = "RELEASE")
  public GateStatusDto overrideGate(
      @PathVariable UUID projectId,
      @PathVariable UUID releaseId,
      @RequestBody OverrideRequest req) {
    return GateStatusDto.from(
        gate.override(projectId, releaseId, req.reason(), CurrentUser.username()));
  }

  /** Override payload — the reason is mandatory and audited. */
  public record OverrideRequest(String reason) {}
}
