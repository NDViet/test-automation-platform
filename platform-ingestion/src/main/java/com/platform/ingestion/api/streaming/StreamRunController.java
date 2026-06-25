package com.platform.ingestion.api.streaming;

import com.platform.ingestion.api.IngestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Real-time streaming ingestion protocol.
 *
 * <pre>
 * 1. POST /api/v1/stream/runs                  → { runId }          — open a session
 * 2. POST /api/v1/stream/runs/{runId}/test      → { accepted: true } — push each test as it completes
 * 3. POST /api/v1/stream/runs/{runId}/heartbeat → 204               — keep-alive during long tests
 * 4. POST /api/v1/stream/runs/{runId}/finish    → IngestResponse    — close &amp; persist
 * </pre>
 *
 * <p>Auth: same X-API-Key header as the batch ingest endpoint.
 */
@RestController
@RequestMapping("/api/v1/stream")
@Tag(
    name = "Streaming Ingestion",
    description = "Real-time test result streaming from Playwright / Node runners")
public class StreamRunController {

  private final StreamingRunService streamingService;

  public StreamRunController(StreamingRunService streamingService) {
    this.streamingService = streamingService;
  }

  @PostMapping("/runs")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Start a streaming run session")
  public StartRunResponse startRun(@RequestBody StartRunRequest request) {
    return streamingService.startRun(request);
  }

  @PostMapping("/runs/{runId}/test")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(summary = "Push a single test result into an open run")
  public PushTestResponse pushTest(
      @PathVariable String runId, @RequestBody TestEventRequest request) {
    try {
      streamingService.pushTest(runId, request);
      return new PushTestResponse(true);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @PostMapping("/runs/{runId}/heartbeat")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Keep-alive ping — resets the inactivity timer for the session")
  public void heartbeat(@PathVariable String runId) {
    try {
      streamingService.heartbeat(runId);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  @PostMapping("/runs/{runId}/finish")
  @Operation(summary = "Finalize the run and persist all accumulated results")
  public IngestResponse finishRun(@PathVariable String runId) {
    try {
      return streamingService.finishRun(runId);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  // ── DTOs ────────────────────────────────────────────────────────────────────

  public record StartRunRequest(
      String orgSlug,
      String projectSlug,
      String teamSlug, // optional — attributes results to a specific team
      String areaSlug, // optional — attributes results to an area/component
      String branch,
      String environment,
      String commitSha,
      String ciRunUrl,
      String ciProvider, // "github" | "gitlab" | "circleci" | … — auto-detected by reporter
      String workflow, // workflow / pipeline name
      String trigger, // raw event name: "push" | "pull_request" | "schedule" | …
      Integer prNumber, // PR / MR number (null on push builds)
      String runNumber, // sequential build counter in CI
      Integer runAttempt, // re-run attempt (1-based; null for first attempt)
      String iterationPath // optional: ADO sprint/iteration path for reporting
      ) {}

  public record StartRunResponse(String runId) {}

  public record TestEventRequest(
      /** Fully-qualified test ID, e.g. "tests/checkout/user.spec.ts::should checkout". */
      String testId,
      /** Human-readable display name (test title). */
      String displayName,
      /** Suite / describe block name, or file path. */
      String suiteName,
      /** Playwright tags from test.tags and @tag annotations. */
      List<String> tags,
      /** "passed" | "failed" | "skipped" | "timedOut" | "interrupted" */
      String status,
      long durationMs,
      String failureMessage,
      String stackTrace,
      int retryCount,

      // ── Tier-1: test location and execution context ──────────────────
      /** Spec file path relative to the project root. */
      String specFile,
      /** Playwright project name: "chromium", "firefox", "webkit", "Mobile Chrome", etc. */
      String browser,
      /**
       * Non-tag Playwright annotations: fixme, slow, fail, skip, and user-defined. Each map has
       * "type" and optional "description" keys.
       */
      List<Map<String, String>> annotations,

      // ── Tier-2: visual artifacts and parallelism ─────────────────────
      boolean hasScreenshot,
      boolean hasVideo,
      /** Playwright worker slot that executed this test (0-based). Null = unknown. */
      Integer workerIndex,

      // ── TIA: Test Impact Analysis declarations ────────────────────────
      /** Source files exercised by this test (via tia.covers()). */
      List<String> coveredFiles,
      /** Logical components exercised (via tia.component()). */
      List<String> coveredComponents,
      /** HTTP or UI routes exercised (via tia.route()). */
      List<String> coveredRoutes,
      /**
       * Arbitrary key-value labels for filtering and ownership. Each map has "key" and "value"
       * entries.
       */
      List<Map<String, String>> labels) {}

  public record PushTestResponse(boolean accepted) {}
}
