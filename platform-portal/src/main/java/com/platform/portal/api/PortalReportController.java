package com.platform.portal.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Portal BFF — CSV report exports. Fetches CSV from ingestion/analytics and re-serves as a
 * download.
 */
@RestController
@RequestMapping("/api/portal/projects/{projectId}/reports")
@Tag(name = "Portal Reports")
public class PortalReportController {

  private final RestClient ingestionClient;
  private final RestClient analyticsClient;

  public PortalReportController(
      @Qualifier("ingestionClient") RestClient ingestionClient,
      @Qualifier("analyticsClient") RestClient analyticsClient) {
    this.ingestionClient = ingestionClient;
    this.analyticsClient = analyticsClient;
  }

  @GetMapping(value = "/traceability.csv", produces = "text/csv")
  public ResponseEntity<String> traceability(@PathVariable String projectId) {
    String csv =
        ingestionClient
            .get()
            .uri("/api/v1/projects/" + projectId + "/reports/traceability.csv")
            .retrieve()
            .body(String.class);
    return download(csv, "traceability.csv");
  }

  @GetMapping(value = "/exec-summary.csv", produces = "text/csv")
  public ResponseEntity<String> execSummary(
      @PathVariable String projectId, @RequestParam(defaultValue = "30") int days) {
    String csv =
        analyticsClient
            .get()
            .uri("/api/v1/projects/" + projectId + "/reports/exec-summary.csv?days=" + days)
            .retrieve()
            .body(String.class);
    return download(csv, "exec-summary.csv");
  }

  private static ResponseEntity<String> download(String body, String filename) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(MediaType.parseMediaType("text/csv"))
        .body(body != null ? body : "");
  }
}
