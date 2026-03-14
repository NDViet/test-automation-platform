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
 * Parses Playwright JSON report (playwright-report/results.json).
 * Structure: { suites[] → specs[] → tests[] → results[] }
 */
@Component
public class PlaywrightParser implements ResultParser {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightParser.class);

    private final ObjectMapper objectMapper;

    public PlaywrightParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public SourceFormat supportedFormat() {
        return SourceFormat.PLAYWRIGHT;
    }

    @Override
    public List<UnifiedTestResult> parse(List<byte[]> files, ParseContext ctx) throws ParseException {
        if (files == null || files.isEmpty()) {
            throw new ParseException("No Playwright JSON files provided", SourceFormat.PLAYWRIGHT);
        }

        List<TestCaseResultDto> allCases = new ArrayList<>();

        for (byte[] file : files) {
            try {
                JsonNode root = objectMapper.readTree(file);
                JsonNode suites = root.path("suites");
                if (!suites.isArray()) {
                    throw new ParseException("Invalid Playwright report: missing 'suites' array",
                            SourceFormat.PLAYWRIGHT);
                }
                parseSuites(suites, "", allCases);
            } catch (ParseException e) {
                throw e;
            } catch (Exception e) {
                throw new ParseException("Failed to parse Playwright JSON: " + e.getMessage(),
                        SourceFormat.PLAYWRIGHT, e);
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
                SourceFormat.PLAYWRIGHT, allCases,
                ctx.executionMode(), ctx.parallelism(), ctx.suiteName()
        ));
    }

    private void parseSuites(JsonNode suites, String parentTitle, List<TestCaseResultDto> results) {
        for (JsonNode suite : suites) {
            String suiteTitle = suite.path("title").asText("");
            String fullTitle  = parentTitle.isBlank() ? suiteTitle
                    : parentTitle + " > " + suiteTitle;
            String file       = suite.path("file").asText(suiteTitle);

            // Recurse into nested suites
            if (suite.has("suites")) {
                parseSuites(suite.path("suites"), fullTitle, results);
            }

            // Process specs in this suite
            JsonNode specs = suite.path("specs");
            if (!specs.isArray()) continue;

            for (JsonNode spec : specs) {
                try {
                    results.add(parseSpec(spec, file, fullTitle));
                } catch (Exception e) {
                    log.warn("Skipping unparseable Playwright spec: {}", e.getMessage());
                }
            }
        }
    }

    private TestCaseResultDto parseSpec(JsonNode spec, String file, String suiteTitle) {
        String specTitle = spec.path("title").asText("unknown");
        String testId    = file + "#" + specTitle;

        // Determine status and duration from tests[].results[]
        TestStatus status = TestStatus.PASSED;
        String failureMessage = null;
        String stackTrace = null;
        long durationMs = 0;

        JsonNode tests = spec.path("tests");
        if (tests.isArray()) {
            for (JsonNode test : tests) {
                JsonNode results = test.path("results");
                if (!results.isArray()) continue;
                for (JsonNode result : results) {
                    durationMs += result.path("duration").asLong(0);
                    String resultStatus = result.path("status").asText("passed");

                    if ("failed".equals(resultStatus) || "unexpected".equals(resultStatus)) {
                        status = TestStatus.FAILED;
                        JsonNode error = result.path("error");
                        if (!error.isMissingNode()) {
                            failureMessage = error.path("message").asText(null);
                            stackTrace     = error.path("stack").asText(failureMessage);
                        }
                    } else if ("skipped".equals(resultStatus) && status == TestStatus.PASSED) {
                        status = TestStatus.SKIPPED;
                    } else if ("timedOut".equals(resultStatus)) {
                        status = TestStatus.BROKEN;
                        failureMessage = "Test timed out";
                    }
                }
            }
        }

        // Extract coveredModules if present (added by @platform/playwright-reporter)
        List<String> coveredClasses = List.of();
        JsonNode coveredModulesNode = spec.path("coveredModules");
        if (coveredModulesNode.isArray()) {
            List<String> modules = new java.util.ArrayList<>();
            for (JsonNode m : coveredModulesNode) {
                if (m.isTextual() && !m.asText().isBlank()) {
                    modules.add(m.asText());
                }
            }
            coveredClasses = modules;
        }

        TestCaseResultDto dto = TestCaseResultDto.basic(
                testId, specTitle, file, specTitle,
                List.of(), status, durationMs,
                failureMessage, stackTrace, 0, List.of()
        );
        // Return with coveredClasses if present (basic() defaults to empty)
        return coveredClasses.isEmpty() ? dto
                : new TestCaseResultDto(
                        dto.testId(), dto.displayName(), dto.className(), dto.methodName(),
                        dto.tags(), dto.status(), dto.durationMs(),
                        dto.failureMessage(), dto.stackTrace(), dto.retryCount(), dto.attachments(),
                        dto.steps(), dto.traceId(), dto.environment(), coveredClasses);
    }
}
