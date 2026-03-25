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
 * Parses Cucumber JSON report (cucumber.json).
 * Structure: Feature[] → Element[] (scenarios) → Step[]
 */
@Component
public class CucumberJsonParser implements ResultParser {

    private static final Logger log = LoggerFactory.getLogger(CucumberJsonParser.class);

    private final ObjectMapper objectMapper;

    public CucumberJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public SourceFormat supportedFormat() {
        return SourceFormat.CUCUMBER_JSON;
    }

    @Override
    public List<UnifiedTestResult> parse(List<byte[]> files, ParseContext ctx) throws ParseException {
        if (files == null || files.isEmpty()) {
            throw new ParseException("No Cucumber JSON files provided", SourceFormat.CUCUMBER_JSON);
        }

        List<TestCaseResultDto> allCases = new ArrayList<>();
        String suiteName = ctx.suiteName();

        for (byte[] file : files) {
            try {
                JsonNode features = objectMapper.readTree(file);
                if (!features.isArray()) {
                    throw new ParseException("Cucumber JSON must be an array of features", SourceFormat.CUCUMBER_JSON);
                }
                for (JsonNode feature : features) {
                    // Use first feature name as suiteName
                    if (suiteName.isBlank()) {
                        String name = feature.path("name").asText("");
                        if (!name.isBlank()) suiteName = name;
                    }
                    parseFeature(feature, allCases);
                }
            } catch (ParseException e) {
                throw e;
            } catch (Exception e) {
                throw new ParseException("Failed to parse Cucumber JSON: " + e.getMessage(),
                        SourceFormat.CUCUMBER_JSON, e);
            }
        }

        int passed  = (int) allCases.stream().filter(t -> t.status() == TestStatus.PASSED).count();
        int failed  = (int) allCases.stream().filter(t -> t.status() == TestStatus.FAILED).count();
        int skipped = (int) allCases.stream().filter(t -> t.status() == TestStatus.SKIPPED).count();
        int broken  = (int) allCases.stream().filter(t -> t.status() == TestStatus.BROKEN).count();

        return List.of(new UnifiedTestResult(
                ctx.runId(), ctx.teamId(), ctx.projectId(),
                ctx.branch(), ctx.environment(), ctx.commitSha(),
                null, ctx.ciProvider(), ctx.ciRunUrl(), Instant.now(),
                allCases.size(), passed, failed, skipped, broken, null,
                SourceFormat.CUCUMBER_JSON, allCases,
                ctx.executionMode(), ctx.parallelism(), suiteName,
                null, null
        ));
    }

    private void parseFeature(JsonNode feature, List<TestCaseResultDto> results) {
        String featureUri  = feature.path("uri").asText("unknown");
        String featureName = feature.path("name").asText("unknown");

        JsonNode elements = feature.path("elements");
        if (!elements.isArray()) return;

        for (JsonNode scenario : elements) {
            try {
                results.add(parseScenario(scenario, featureUri, featureName));
            } catch (Exception e) {
                log.warn("Skipping unparseable Cucumber scenario: {}", e.getMessage());
            }
        }
    }

    private TestCaseResultDto parseScenario(JsonNode scenario, String featureUri, String featureName) {
        String scenarioName = scenario.path("name").asText("unknown");
        String testId       = featureUri + "#" + scenarioName;

        // Tags from scenario + feature
        List<String> tags = new ArrayList<>();
        scenario.path("tags").forEach(t -> tags.add(t.path("name").asText()));

        // Determine status from steps
        TestStatus status = TestStatus.PASSED;
        String failureMessage = null;
        String stackTrace = null;
        long totalDurationNs = 0;

        JsonNode steps = scenario.path("steps");
        if (steps.isArray()) {
            for (JsonNode step : steps) {
                JsonNode result = step.path("result");
                String stepStatus = result.path("status").asText("passed");
                totalDurationNs += result.path("duration").asLong(0);

                if ("failed".equals(stepStatus) && status != TestStatus.FAILED) {
                    status = TestStatus.FAILED;
                    failureMessage = result.path("error_message").asText(null);
                    if (failureMessage != null && failureMessage.contains("\n")) {
                        stackTrace = failureMessage;
                        failureMessage = failureMessage.substring(0, failureMessage.indexOf('\n')).strip();
                    }
                } else if ("skipped".equals(stepStatus) && status == TestStatus.PASSED) {
                    status = TestStatus.SKIPPED;
                } else if ("pending".equals(stepStatus) && status == TestStatus.PASSED) {
                    status = TestStatus.SKIPPED;
                }
            }
        }

        long durationMs = totalDurationNs / 1_000_000; // nanoseconds to milliseconds

        return TestCaseResultDto.basic(
                testId, scenarioName, featureName, scenarioName,
                tags, status, durationMs, failureMessage, stackTrace, 0, List.of()
        );
    }
}
