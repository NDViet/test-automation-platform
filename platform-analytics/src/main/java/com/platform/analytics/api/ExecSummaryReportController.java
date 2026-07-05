package com.platform.analytics.api;

import com.platform.analytics.report.ReleaseReportDto;
import com.platform.analytics.report.ReleaseReportService;
import com.platform.common.report.CsvWriter;
import com.platform.security.authz.Capability;
import com.platform.security.web.RequireCapability;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** CSV exec-summary export (read-only) — a key/value snapshot of the release window aggregates. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/reports")
@RequireCapability(value = Capability.VIEW_RESULTS, scope = "projectId")
@Tag(name = "Reports")
public class ExecSummaryReportController {

  private final ReleaseReportService reportService;

  public ExecSummaryReportController(ReleaseReportService reportService) {
    this.reportService = reportService;
  }

  @GetMapping(value = "/exec-summary.csv", produces = "text/csv")
  public ResponseEntity<String> execSummary(
      @PathVariable UUID projectId, @RequestParam(defaultValue = "30") int days) {
    Instant to = Instant.now();
    Instant from = to.minus(Math.max(1, days), ChronoUnit.DAYS);
    ReleaseReportDto r = reportService.generate(projectId, from, to, null);

    StringBuilder sb = new StringBuilder();
    sb.append(CsvWriter.row(List.of("Metric", "Value"))).append("\n");
    sb.append(kv("Total runs", r.totalRuns()));
    sb.append(kv("Total tests", r.totalTests()));
    sb.append(kv("Passed", r.totalPassed()));
    sb.append(kv("Failed", r.totalFailed()));
    sb.append(kv("Broken", r.totalBroken()));
    sb.append(kv("Skipped", r.totalSkipped()));
    sb.append(
        kv("Pass rate", String.format(java.util.Locale.US, "%.1f%%", r.overallPassRate() * 100)));
    sb.append(kv("Flaky tests", r.flakyTestCount()));
    sb.append(kv("Critical-flaky", r.criticalFlakyCount()));
    sb.append(kv("Quality gate", r.qualityGatePassed() ? "PASS" : "FAIL"));

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"exec-summary.csv\"")
        .contentType(MediaType.parseMediaType("text/csv"))
        .body(sb.toString());
  }

  private static String kv(String k, Object v) {
    return CsvWriter.row(List.of(k, v)) + "\n";
  }
}
