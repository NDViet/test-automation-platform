package com.platform.ingestion.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.dto.TestCaseResultDto;
import com.platform.common.dto.UnifiedTestResult;
import com.platform.common.enums.SourceFormat;
import com.platform.common.enums.TestStatus;
import com.platform.ingestion.exception.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses Gatling {@code stats.json} simulation report.
 *
 * <p>Structure: {@code contents[key]} where {@code type == "REQUEST"} → one test case per request.
 * Nested {@code GROUP} contents are also traversed recursively.
 * A request is FAILED if {@code stats.numberOfRequests.ko > 0}.</p>
 *
 * <p>File location: {@code target/gatling/<simulation>/js/stats.json}</p>
 */
@Component
public class GatlingStatsJsonParser implements ResultParser {

    private static final Logger log = LoggerFactory.getLogger(GatlingStatsJsonParser.class);

    private final ObjectMapper objectMapper;

    public GatlingStatsJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public SourceFormat supportedFormat() {
        return SourceFormat.GATLING;
    }

    @Override
    public List<UnifiedTestResult> parse(List<byte[]> files, ParseContext ctx) throws ParseException {
        if (files == null || files.isEmpty()) {
            throw new ParseException("No Gatling stats JSON files provided", SourceFormat.GATLING);
        }

        List<TestCaseResultDto> allCases = new ArrayList<>();

        for (byte[] file : files) {
            try {
                JsonNode root = objectMapper.readTree(file);
                JsonNode contents = root.path("contents");
                if (contents.isMissingNode()) {
                    throw new ParseException("Invalid Gatling stats.json: missing 'contents'", SourceFormat.GATLING);
                }
                parseContents(contents, allCases);
            } catch (ParseException e) {
                throw e;
            } catch (Exception e) {
                throw new ParseException("Failed to parse Gatling JSON: " + e.getMessage(), SourceFormat.GATLING, e);
            }
        }

        int passed  = (int) allCases.stream().filter(t -> t.status() == TestStatus.PASSED).count();
        int failed  = (int) allCases.stream().filter(t -> t.status() == TestStatus.FAILED).count();
        int skipped = (int) allCases.stream().filter(t -> t.status() == TestStatus.SKIPPED).count();
        int broken  = (int) allCases.stream().filter(t -> t.status() == TestStatus.BROKEN).count();
        long totalMs = allCases.stream()
                .mapToLong(t -> t.durationMs() != null ? t.durationMs() : 0L).sum();

        return List.of(new UnifiedTestResult(
                ctx.runId(), ctx.teamId(), ctx.projectId(),
                ctx.branch(), ctx.environment(), ctx.commitSha(),
                null, ctx.ciProvider(), ctx.ciRunUrl(), Instant.now(),
                allCases.size(), passed, failed, skipped, broken,
                totalMs > 0 ? totalMs : null,
                SourceFormat.GATLING, allCases,
                ctx.executionMode(), ctx.parallelism(), ctx.suiteName()
        ));
    }

    private void parseContents(JsonNode contents, List<TestCaseResultDto> results) {
        contents.fields().forEachRemaining(entry -> {
            JsonNode node = entry.getValue();
            String type = node.path("type").asText("");

            if ("REQUEST".equals(type)) {
                try {
                    results.add(parseRequest(node));
                } catch (Exception e) {
                    log.warn("Skipping unparseable Gatling request: {}", e.getMessage());
                }
            } else if ("GROUP".equals(type)) {
                // Recurse into group contents
                JsonNode nestedContents = node.path("contents");
                if (!nestedContents.isMissingNode()) {
                    parseContents(nestedContents, results);
                }
            }
        });
    }

    private TestCaseResultDto parseRequest(JsonNode node) {
        String name = node.path("name").asText("unknown");
        String path = node.path("path").asText(name);

        JsonNode stats = node.path("stats");
        JsonNode counts = stats.path("numberOfRequests");
        long total = counts.path("total").asLong(0);
        long ok    = counts.path("ok").asLong(0);
        long ko    = counts.path("ko").asLong(0);

        long meanMs = stats.path("meanResponseTime").path("total").asLong(0);
        long p95Ms  = stats.path("percentiles3").path("total").asLong(0);

        TestStatus status;
        String failureMessage = null;

        if (ko == 0) {
            status = TestStatus.PASSED;
        } else {
            status = TestStatus.FAILED;
            double failRate = total > 0 ? (double) ko / total * 100 : 100.0;
            failureMessage = String.format(
                    "%.1f%% error rate (%d/%d requests failed); mean=%.0fms p95=%.0fms",
                    failRate, ko, total, (double) meanMs, (double) p95Ms);
        }

        return TestCaseResultDto.basic(
                path, name, path, name,
                List.of(), status, meanMs,
                failureMessage, null, 0, List.of()
        );
    }
}
