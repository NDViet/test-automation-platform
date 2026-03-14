package com.platform.analytics.api;

import com.platform.analytics.report.ReleaseReportDto;
import com.platform.analytics.report.ReleaseReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * REST endpoint for release quality reports.
 *
 * <pre>
 * GET /api/v1/projects/{projectId}/release-report
 *     ?tag=v1.2.0          (optional)
 *     &days=14             (default 14, overridden by from/to)
 *     &from=2026-01-01T00:00:00Z  (optional ISO instant)
 *     &to=2026-01-15T00:00:00Z    (optional ISO instant)
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/release-report")
@Tag(name = "Release Report", description = "Quality snapshot for a release window")
public class ReleaseReportController {

    private final ReleaseReportService reportService;

    public ReleaseReportController(ReleaseReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    @Operation(summary = "Generate a quality report for a release window")
    public ResponseEntity<ReleaseReportDto> generate(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "14") int days,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant to) {

        Instant effectiveTo   = to   != null ? to   : Instant.now();
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(days, ChronoUnit.DAYS);

        try {
            ReleaseReportDto report = reportService.generate(
                    projectId, effectiveFrom, effectiveTo, tag);
            return ResponseEntity.ok(report);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
