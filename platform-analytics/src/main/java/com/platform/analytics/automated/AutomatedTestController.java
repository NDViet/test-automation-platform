package com.platform.analytics.automated;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Analytics endpoints for the automated test catalog.
 *
 * <pre>
 * GET /api/v1/analytics/{projectId}/automated-tests
 *     ?days=30 &search=checkout &status=ALL
 *     &tags=smoke &browsers=chromium,firefox &annotationTypes=fixme
 *     &labelKey=owner &labelValue=alice &specFile=tests/checkout/
 *     → List of per-test aggregated summaries
 *
 * GET /api/v1/analytics/{projectId}/automated-tests/tags              → distinct tag values
 * GET /api/v1/analytics/{projectId}/automated-tests/browsers           → distinct browser names
 * GET /api/v1/analytics/{projectId}/automated-tests/annotation-types   → distinct annotation types
 * GET /api/v1/analytics/{projectId}/automated-tests/label-keys         → distinct label keys
 * GET /api/v1/analytics/{projectId}/automated-tests/label-values?labelKey=owner → distinct values
 *
 * GET /api/v1/analytics/{projectId}/automated-tests/detail
 *     ?testId=…&days=30
 *     → Daily trend + last 15 individual run results
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/analytics/{projectId}/automated-tests")
@Tag(name = "Automated Tests", description = "Per-test pass/fail analytics and execution trends")
public class AutomatedTestController {

    private final AutomatedTestService service;

    public AutomatedTestController(AutomatedTestService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List all discovered automated tests with aggregated stats")
    public List<AutomatedTestSummaryDto> list(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) List<String> browsers,
            @RequestParam(required = false) List<String> annotationTypes,
            @RequestParam(required = false) String labelKey,
            @RequestParam(required = false) String labelValue,
            @RequestParam(required = false) String specFile) {
        return service.getSummaries(projectId, days, search, status, tags,
                browsers, annotationTypes, labelKey, labelValue, specFile);
    }

    @GetMapping("/tags")
    @Operation(summary = "Distinct tags seen in the project within the time window")
    public List<String> tags(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "30") int days) {
        return service.getProjectTags(projectId, days);
    }

    @GetMapping("/browsers")
    @Operation(summary = "Distinct Playwright project names (browsers/devices) seen within the time window")
    public List<String> browsers(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "30") int days) {
        return service.getProjectBrowsers(projectId, days);
    }

    @GetMapping("/annotation-types")
    @Operation(summary = "Distinct annotation types (fixme, slow, fail, …) seen within the time window")
    public List<String> annotationTypes(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "30") int days) {
        return service.getProjectAnnotationTypes(projectId, days);
    }

    @GetMapping("/label-keys")
    @Operation(summary = "Distinct label keys (owner, jira, team, …) seen within the time window")
    public List<String> labelKeys(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "30") int days) {
        return service.getProjectLabelKeys(projectId, days);
    }

    @GetMapping("/label-values")
    @Operation(summary = "Distinct values for a specific label key within the time window")
    public List<String> labelValues(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam String labelKey) {
        return service.getProjectLabelValues(projectId, days, labelKey);
    }

    @GetMapping("/detail")
    @Operation(summary = "Daily execution trend + recent runs for a single test")
    public AutomatedTestDetailDto detail(
            @PathVariable UUID projectId,
            @RequestParam String testId,
            @RequestParam(defaultValue = "30") int days) {
        return service.getDetail(projectId, testId, days);
    }
}
