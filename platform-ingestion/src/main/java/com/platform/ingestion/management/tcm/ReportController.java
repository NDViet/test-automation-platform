package com.platform.ingestion.management.tcm;

import com.platform.common.report.CsvWriter;
import com.platform.security.authz.Capability;
import com.platform.security.web.RequireCapability;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CSV report exports (read-only). */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/reports")
@RequireCapability(value = Capability.VIEW_RESULTS, scope = "projectId")
@Tag(name = "Reports")
public class ReportController {

  private final CoverageService coverage;

  public ReportController(CoverageService coverage) {
    this.coverage = coverage;
  }

  /** Traceability matrix: one row per requirement → its case coverage and last observed result. */
  @GetMapping(value = "/traceability.csv", produces = "text/csv")
  public ResponseEntity<String> traceability(@PathVariable UUID projectId) {
    CoverageDto cov = coverage.coverage(projectId);
    StringBuilder sb = new StringBuilder();
    sb.append(
            CsvWriter.row(
                List.of(
                    "Requirement ID",
                    "External ID",
                    "Title",
                    "Type",
                    "Status",
                    "Area",
                    "Team",
                    "Automated cases",
                    "Manual cases",
                    "Last result")))
        .append("\n");
    for (CoverageDto.Row r : cov.requirements()) {
      sb.append(
              CsvWriter.row(
                  Arrays.asList(
                      r.requirementId(),
                      r.externalId(),
                      r.title(),
                      r.issueType(),
                      r.requirementStatus(),
                      r.areaPath(),
                      r.teamName(),
                      r.automatedCases(),
                      r.manualCases(),
                      r.lastStatus())))
          .append("\n");
    }
    return csv(sb.toString(), "traceability.csv");
  }

  static ResponseEntity<String> csv(String body, String filename) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(MediaType.parseMediaType("text/csv"))
        .body(body);
  }
}
