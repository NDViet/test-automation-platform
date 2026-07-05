package com.platform.ingestion.quality;

import com.platform.core.quality.FailureClusterService;
import com.platform.ingestion.audit.AuditLog;
import com.platform.security.authz.Capability;
import com.platform.security.web.RequireCapability;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Failure clusters for a project: browse, suppress as known-issue / resolve, and link or create a
 * tracker defect. Reads require VIEW; every mutation requires OPERATE_QUALITY. Creating a defect
 * and changing status are audited because they have off-platform / policy consequences.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/failure-clusters")
@RequireCapability(value = Capability.VIEW_RESULTS, scope = "projectId")
@Tag(name = "Failure Clusters")
public class FailureClusterController {

  private final FailureClusterService clusters;
  private final FailureClusterDefectService defects;
  private final FailureClusterRetestService retest;

  public FailureClusterController(
      FailureClusterService clusters,
      FailureClusterDefectService defects,
      FailureClusterRetestService retest) {
    this.clusters = clusters;
    this.defects = defects;
    this.retest = retest;
  }

  @GetMapping
  public List<FailureClusterDto> list(
      @PathVariable UUID projectId, @RequestParam(required = false) String status) {
    return clusters.list(projectId, status).stream().map(FailureClusterDto::from).toList();
  }

  @GetMapping("/{id}")
  public FailureClusterDto get(@PathVariable UUID projectId, @PathVariable UUID id) {
    return FailureClusterDto.from(clusters.get(projectId, id));
  }

  @PatchMapping("/{id}")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  @AuditLog(eventType = "FAILURE_CLUSTER_STATUS", resourceType = "FAILURE_CLUSTER")
  public FailureClusterDto updateStatus(
      @PathVariable UUID projectId, @PathVariable UUID id, @RequestBody UpdateStatusRequest req) {
    return FailureClusterDto.from(clusters.updateStatus(projectId, id, req.status()));
  }

  @PostMapping("/{id}/link-defect")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public FailureClusterDto linkDefect(
      @PathVariable UUID projectId, @PathVariable UUID id, @RequestBody LinkDefectRequest req) {
    return FailureClusterDto.from(
        clusters.linkDefect(projectId, id, req.externalId(), req.url(), req.title(), req.state()));
  }

  @PostMapping("/{id}/create-defect")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  @AuditLog(eventType = "TICKET_CREATED", resourceType = "FAILURE_CLUSTER")
  public FailureClusterDto createDefect(
      @PathVariable UUID projectId, @PathVariable UUID id, @RequestBody CreateDefectRequest req) {
    return FailureClusterDto.from(
        defects.createDefect(projectId, id, req.issueType(), req.priority(), req.description()));
  }

  @PostMapping("/{id}/retest")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  @AuditLog(eventType = "FAILURE_CLUSTER_RETEST", resourceType = "FAILURE_CLUSTER")
  public RetestResponse retest(@PathVariable UUID projectId, @PathVariable UUID id) {
    var run = retest.retest(projectId, id, com.platform.security.web.CurrentUser.username());
    return new RetestResponse(run.getId(), run.getName(), run.getStatus());
  }

  /** Sets the cluster status (OPEN / KNOWN_ISSUE / RESOLVED). */
  public record UpdateStatusRequest(String status) {}

  /** The targeted retest run created for a cluster. */
  public record RetestResponse(UUID runId, String name, String status) {}

  /** Links the cluster to an existing tracker defect. */
  public record LinkDefectRequest(String externalId, String url, String title, String state) {}

  /** Creates a new tracker defect; all fields optional (sensible defaults applied server-side). */
  public record CreateDefectRequest(String issueType, String priority, String description) {}
}
