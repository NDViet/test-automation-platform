package com.platform.analytics.search;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for querying test execution logs stored in OpenSearch.
 *
 * <p>All logs produced during a test JVM run are tagged with {@code run_id}
 * (shared across the entire suite) and {@code test_id} (per scenario/method).
 * Use these endpoints to retrieve the exact log stream for any run or test case.</p>
 *
 * <h3>Quick reference</h3>
 * <pre>
 *   GET /api/v1/logs/runs/{runId}           → all logs for a test run
 *   GET /api/v1/logs/tests/{testId}         → logs for one scenario / method
 *   GET /api/v1/logs/runs?team=..&project=..→ recent run IDs for a project
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/logs")
@Tag(name = "Log Search", description = "Query test execution logs from OpenSearch")
public class LogSearchController {

    private final LogSearchService logSearchService;

    public LogSearchController(LogSearchService logSearchService) {
        this.logSearchService = logSearchService;
    }

    // ── By run_id ─────────────────────────────────────────────────────────────

    /**
     * Returns log entries for an entire test run, identified by its {@code run_id}.
     *
     * <p>The {@code run_id} is the value of {@code RunContext.getRunId()} in the test
     * JVM — a UUID that is the same for all tests in one {@code mvn test} invocation.
     * It is printed at the start of each test run by PlatformExtension / PlatformCucumberPlugin.</p>
     */
    @GetMapping("/runs/{runId}")
    @Operation(summary = "Get all logs for a test run",
               description = "Returns chronologically sorted log lines tagged with the given run_id. " +
                             "Filter by level (INFO/WARN/ERROR) to focus on failures.")
    public ResponseEntity<LogSearchResponse> getRunLogs(
            @PathVariable String runId,
            @Parameter(description = "Max entries to return (default 500, max 2000)")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Filter by log level: INFO, WARN, ERROR")
            @RequestParam(required = false) String level) {

        List<LogEntry> entries = logSearchService.findByRunId(runId, size, level);
        return ResponseEntity.ok(new LogSearchResponse(runId, null, entries.size(), entries));
    }

    // ── By test_id ────────────────────────────────────────────────────────────

    /**
     * Returns log entries for a specific test case (scenario or JUnit method),
     * identified by its {@code test_id}.
     *
     * <p>The {@code test_id} format is {@code ClassName#methodName} for JUnit5 tests
     * and {@code featureFile#Scenario_name} for Cucumber scenarios.</p>
     */
    @GetMapping("/tests/{testId}")
    @Operation(summary = "Get logs for a specific test case",
               description = "Returns log lines emitted during a single scenario or test method. " +
                             "Includes step transitions, assertions, and any attached stack traces.")
    public ResponseEntity<LogSearchResponse> getTestLogs(
            @PathVariable String testId,
            @Parameter(description = "Max entries to return (default 500, max 2000)")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Filter by log level: INFO, WARN, ERROR")
            @RequestParam(required = false) String level) {

        List<LogEntry> entries = logSearchService.findByTestId(testId, size, level);
        return ResponseEntity.ok(new LogSearchResponse(null, testId, entries.size(), entries));
    }

    // ── List recent run IDs ───────────────────────────────────────────────────

    /**
     * Returns the distinct {@code run_id} values seen in the last N days for a
     * given team + project. Use this to build a "recent runs" picker in the portal.
     */
    @GetMapping("/runs")
    @Operation(summary = "List recent run IDs for a project",
               description = "Returns distinct run_id values from the last N days, " +
                             "newest first. Useful for building a run selector in the UI.")
    public ResponseEntity<List<String>> listRecentRuns(
            @Parameter(description = "Team slug", required = true)
            @RequestParam String teamId,
            @Parameter(description = "Project slug", required = true)
            @RequestParam String projectId,
            @Parameter(description = "Look-back window in days (default 7)")
            @RequestParam(defaultValue = "7") int days) {

        List<String> runIds = logSearchService.listRecentRunIds(teamId, projectId, days);
        return ResponseEntity.ok(runIds);
    }

    // ── Response envelope ─────────────────────────────────────────────────────

    public record LogSearchResponse(
            String runId,
            String testId,
            int    total,
            List<LogEntry> logs
    ) {}
}
