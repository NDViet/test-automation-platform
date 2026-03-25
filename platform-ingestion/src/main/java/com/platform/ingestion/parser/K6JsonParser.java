package com.platform.ingestion.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.PerformanceMetricsDto;
import com.platform.common.dto.TestCaseResultDto;
import com.platform.common.dto.UnifiedTestResult;
import com.platform.common.enums.SourceFormat;
import com.platform.common.enums.TestStatus;
import com.platform.common.enums.TestType;
import com.platform.ingestion.exception.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses k6 {@code --summary-export} JSON output.
 *
 * <p>Structure: {@code root_group.checks[]} + {@code root_group.groups[].checks[]} (recursive).
 * Each check becomes one test case: passes=0 → FAILED, passes>0 && fails=0 → PASSED,
 * passes>0 && fails>0 → FAILED (partial failure).</p>
 *
 * <p>Generate the file with: {@code k6 run --summary-export=summary.json script.js}</p>
 */
@Component
public class K6JsonParser implements ResultParser {

    private static final Logger log = LoggerFactory.getLogger(K6JsonParser.class);

    private final ObjectMapper objectMapper;

    public K6JsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public SourceFormat supportedFormat() {
        return SourceFormat.K6;
    }

    @Override
    public List<UnifiedTestResult> parse(List<byte[]> files, ParseContext ctx) throws ParseException {
        if (files == null || files.isEmpty()) {
            throw new ParseException("No k6 summary JSON files provided", SourceFormat.K6);
        }

        List<TestCaseResultDto> allCases = new ArrayList<>();
        PerformanceMetricsDto perfMetrics = null;

        for (byte[] file : files) {
            try {
                JsonNode root = objectMapper.readTree(file);
                JsonNode rootGroup = root.path("root_group");
                if (rootGroup.isMissingNode()) {
                    throw new ParseException("Invalid k6 summary: missing 'root_group'", SourceFormat.K6);
                }
                parseGroup(rootGroup, "", allCases);

                // Extract rich performance metrics from the top-level "metrics" section
                JsonNode metricsNode = root.path("metrics");
                if (!metricsNode.isMissingNode()) {
                    perfMetrics = extractPerformanceMetrics(metricsNode, root);
                }
            } catch (ParseException e) {
                throw e;
            } catch (Exception e) {
                throw new ParseException("Failed to parse k6 JSON: " + e.getMessage(), SourceFormat.K6, e);
            }
        }

        int passed  = (int) allCases.stream().filter(t -> t.status() == TestStatus.PASSED).count();
        int failed  = (int) allCases.stream().filter(t -> t.status() == TestStatus.FAILED).count();
        int skipped = (int) allCases.stream().filter(t -> t.status() == TestStatus.SKIPPED).count();
        int broken  = (int) allCases.stream().filter(t -> t.status() == TestStatus.BROKEN).count();
        long totalMs = allCases.stream()
                .mapToLong(t -> t.durationMs() != null ? t.durationMs() : 0L).sum();

        // Prefer perfMetrics duration (actual load-test wall-clock) over sum of check durations
        Long durationMs = (perfMetrics != null && perfMetrics.durationMs() != null)
                ? perfMetrics.durationMs()
                : (totalMs > 0 ? totalMs : null);

        return List.of(new UnifiedTestResult(
                ctx.runId(), ctx.teamId(), ctx.projectId(),
                ctx.branch(), ctx.environment(), ctx.commitSha(),
                null, ctx.ciProvider(), ctx.ciRunUrl(), Instant.now(),
                allCases.size(), passed, failed, skipped, broken,
                durationMs,
                SourceFormat.K6, allCases,
                ctx.executionMode(), ctx.parallelism(), ctx.suiteName(),
                TestType.PERFORMANCE, perfMetrics
        ));
    }

    private void parseGroup(JsonNode group, String parentPath, List<TestCaseResultDto> results) {
        String groupName = group.path("name").asText("");
        String groupPath = parentPath.isEmpty() ? groupName
                : (groupName.isEmpty() ? parentPath : parentPath + " / " + groupName);

        // Parse checks in this group
        JsonNode checks = group.path("checks");
        if (checks.isArray()) {
            for (JsonNode check : checks) {
                try {
                    results.add(parseCheck(check, groupPath));
                } catch (Exception e) {
                    log.warn("Skipping unparseable k6 check: {}", e.getMessage());
                }
            }
        }

        // Recurse into nested groups
        JsonNode groups = group.path("groups");
        if (groups.isArray()) {
            for (JsonNode subGroup : groups) {
                parseGroup(subGroup, groupPath, results);
            }
        }
    }

    /**
     * Extracts HTTP response time percentiles, throughput, error rate, and VU count
     * from the top-level {@code metrics} node of a k6 summary JSON.
     *
     * <p>K6 metric keys of interest:
     * <ul>
     *   <li>{@code http_req_duration} — avg/min/med/max/p(90)/p(95)/p(99) in ms</li>
     *   <li>{@code http_reqs} — count (total requests), rate (req/s)</li>
     *   <li>{@code http_req_failed} — passes/fails used to compute error rate</li>
     *   <li>{@code vus_max} — value (peak concurrent VUs)</li>
     *   <li>{@code test_duration} — test wall-clock duration in ms (custom metric or inferred)</li>
     * </ul>
     */
    private PerformanceMetricsDto extractPerformanceMetrics(JsonNode metrics, JsonNode root) {
        JsonNode dur = metrics.path("http_req_duration");
        Double avgMs    = doubleOrNull(dur, "avg");
        Double minMs    = doubleOrNull(dur, "min");
        Double medianMs = doubleOrNull(dur, "med");
        Double maxMs    = doubleOrNull(dur, "max");
        Double p90Ms    = doubleOrNull(dur, "p(90)");
        Double p95Ms    = doubleOrNull(dur, "p(95)");
        Double p99Ms    = doubleOrNull(dur, "p(99)");

        JsonNode reqs = metrics.path("http_reqs");
        Long   requestsTotal    = longOrNull(reqs, "count");
        Double requestsPerSecond = doubleOrNull(reqs, "rate");

        // Error rate: fails / (passes + fails)
        Double errorRate = null;
        JsonNode failed = metrics.path("http_req_failed");
        if (!failed.isMissingNode()) {
            long passes = failed.path("passes").asLong(0);
            long fails  = failed.path("fails").asLong(0);
            long total  = passes + fails;
            if (total > 0) errorRate = (double) fails / total;
        }

        JsonNode vusMax = metrics.path("vus_max");
        Integer vusMaxVal = vusMax.isMissingNode() ? null : (int) vusMax.path("value").asLong(0);

        // Wall-clock duration: prefer test_duration metric, fall back to iteration_duration max
        Long durationMs = null;
        JsonNode testDur = metrics.path("test_duration");
        if (!testDur.isMissingNode()) {
            double v = testDur.path("value").asDouble(0);
            if (v > 0) durationMs = (long) v;
        }
        if (durationMs == null) {
            JsonNode iterDur = metrics.path("iteration_duration");
            Double maxIter = doubleOrNull(iterDur, "max");
            if (maxIter != null && maxIter > 0) durationMs = maxIter.longValue();
        }

        return PerformanceMetricsDto.of(
                avgMs, minMs, medianMs, maxMs, p90Ms, p95Ms, p99Ms,
                requestsTotal, requestsPerSecond,
                errorRate, vusMaxVal, durationMs);
    }

    private static Double doubleOrNull(JsonNode node, String field) {
        if (node.isMissingNode()) return null;
        JsonNode f = node.path(field);
        return f.isMissingNode() ? null : f.asDouble();
    }

    private static Long longOrNull(JsonNode node, String field) {
        if (node.isMissingNode()) return null;
        JsonNode f = node.path(field);
        return f.isMissingNode() ? null : f.asLong();
    }

    private TestCaseResultDto parseCheck(JsonNode check, String groupPath) {
        String name   = check.path("name").asText("unknown");
        long passes   = check.path("passes").asLong(0);
        long fails    = check.path("fails").asLong(0);
        long total    = passes + fails;

        String testId = groupPath.isEmpty() ? name : groupPath + " / " + name;

        TestStatus status;
        String failureMessage = null;

        if (fails == 0 && passes > 0) {
            status = TestStatus.PASSED;
        } else if (passes == 0 && fails > 0) {
            status = TestStatus.FAILED;
            failureMessage = String.format("Check failed: 0/%d iterations passed", total);
        } else if (fails > 0) {
            status = TestStatus.FAILED;
            double failRate = (double) fails / total * 100;
            failureMessage = String.format("Check partially failed: %.1f%% failure rate (%d/%d failed)",
                    failRate, fails, total);
        } else {
            // passes == 0 && fails == 0 — no data
            status = TestStatus.SKIPPED;
        }

        return TestCaseResultDto.basic(
                testId, name, groupPath.isEmpty() ? "root" : groupPath, name,
                List.of(), status, 0L,
                failureMessage, null, 0, List.of()
        );
    }
}
