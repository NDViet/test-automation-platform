package com.platform.agent.api;

import com.platform.agent.hub.sync.AzureOrgSyncService;
import com.platform.agent.hub.sync.WorkItemHistorySyncService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** On-demand sync of the ADO org structure (teams / areas / iterations / users) for a project. */
@RestController
@RequestMapping("/api/agent/projects")
public class AdoStructureSyncController {

  private static final Logger log = LoggerFactory.getLogger(AdoStructureSyncController.class);

  private static final List<String> DEFAULT_HISTORY_TYPES = List.of("DEFECT", "STORY", "TASK");

  private final AzureOrgSyncService orgSyncService;
  private final WorkItemHistorySyncService historySyncService;

  public AdoStructureSyncController(
      AzureOrgSyncService orgSyncService, WorkItemHistorySyncService historySyncService) {
    this.orgSyncService = orgSyncService;
    this.historySyncService = historySyncService;
  }

  @PostMapping("/{projectId}/ado/sync-structure")
  public ResponseEntity<Map<String, Object>> syncStructure(@PathVariable UUID projectId) {
    try {
      AzureOrgSyncService.SyncResult r = orgSyncService.syncProject(projectId);
      return ResponseEntity.ok(
          Map.of(
              "success",
              true,
              "teams",
              r.teams(),
              "areas",
              r.areas(),
              "iterations",
              r.iterations(),
              "users",
              r.users()));
    } catch (IllegalArgumentException | IllegalStateException e) {
      return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
    } catch (Exception e) {
      log.error("ADO structure sync failed for project {}: {}", projectId, e.getMessage(), e);
      return ResponseEntity.internalServerError()
          .body(Map.of("success", false, "error", e.getMessage()));
    }
  }

  @PostMapping("/{projectId}/quality/sync-history")
  public ResponseEntity<Map<String, Object>> syncHistory(
      @PathVariable UUID projectId, @RequestParam(required = false) List<String> types) {
    List<String> issueTypes = (types == null || types.isEmpty()) ? DEFAULT_HISTORY_TYPES : types;
    boolean started = historySyncService.startSync(projectId, issueTypes);
    return ResponseEntity.ok(
        Map.of(
            "success",
            true,
            "started",
            started,
            "status",
            started ? "started" : "already-running",
            "message",
            started
                ? "History sync running in the background — refresh in a minute to see results."
                : "A history sync is already in progress for this project."));
  }

  @GetMapping("/{projectId}/quality/history-status")
  public Map<String, Object> historyStatus(@PathVariable UUID projectId) {
    return Map.of("running", historySyncService.isRunning(projectId));
  }
}
