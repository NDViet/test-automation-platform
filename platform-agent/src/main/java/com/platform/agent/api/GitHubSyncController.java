package com.platform.agent.api;

import com.platform.agent.hub.polling.GitHubPollingService;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * On-demand GitHub sync endpoint for projects using PAT + polling instead of webhooks. Useful for
 * CI pipelines or UI "Sync now" buttons that can't wait for the next poll interval.
 *
 * <p>POST /hub/integrations/github/{projectId}/sync Immediately polls GitHub for new/updated PRs
 * and triggers analysis workflows. Returns 200 with {"triggered": N}.
 */
@RestController
@RequestMapping("/hub/integrations/github")
public class GitHubSyncController {

  private static final Logger log = LoggerFactory.getLogger(GitHubSyncController.class);

  private final GitHubPollingService pollingService;

  public GitHubSyncController(GitHubPollingService pollingService) {
    this.pollingService = pollingService;
  }

  @PostMapping("/{projectId}/sync")
  public ResponseEntity<Map<String, Object>> syncNow(@PathVariable UUID projectId) {
    log.info("on-demand GitHub sync requested for project {}", projectId);
    try {
      int triggered = pollingService.syncNow(projectId);
      return ResponseEntity.ok(
          Map.of(
              "projectId",
              projectId.toString(),
              "triggered",
              triggered,
              "message",
              triggered == 0
                  ? "No new or updated PRs since last poll."
                  : triggered + " workflow(s) triggered."));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    } catch (Exception e) {
      log.error("on-demand sync failed for project {}: {}", projectId, e.getMessage(), e);
      return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
    }
  }
}
