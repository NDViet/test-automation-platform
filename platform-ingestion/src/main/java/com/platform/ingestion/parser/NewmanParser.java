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
 * Parses Postman/Newman JSON reporter output.
 * Each execution item → each assertion = one test case.
 */
@Component
public class NewmanParser implements ResultParser {

    private static final Logger log = LoggerFactory.getLogger(NewmanParser.class);

    private final ObjectMapper objectMapper;

    public NewmanParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public SourceFormat supportedFormat() {
        return SourceFormat.NEWMAN;
    }

    @Override
    public List<UnifiedTestResult> parse(List<byte[]> files, ParseContext ctx) throws ParseException {
        if (files == null || files.isEmpty()) {
            throw new ParseException("No Newman JSON files provided", SourceFormat.NEWMAN);
        }

        List<TestCaseResultDto> allCases = new ArrayList<>();

        for (byte[] file : files) {
            try {
                JsonNode root  = objectMapper.readTree(file);
                JsonNode run   = root.path("run");
                JsonNode executions = run.path("executions");

                if (!executions.isArray()) {
                    throw new ParseException("Invalid Newman report: missing run.executions",
                            SourceFormat.NEWMAN);
                }

                for (JsonNode execution : executions) {
                    try {
                        parseExecution(execution, allCases);
                    } catch (Exception e) {
                        log.warn("Skipping unparseable Newman execution: {}", e.getMessage());
                    }
                }
            } catch (ParseException e) {
                throw e;
            } catch (Exception e) {
                throw new ParseException("Failed to parse Newman JSON: " + e.getMessage(),
                        SourceFormat.NEWMAN, e);
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
                SourceFormat.NEWMAN, allCases,
                ctx.executionMode(), ctx.parallelism(), ctx.suiteName()
        ));
    }

    private void parseExecution(JsonNode execution, List<TestCaseResultDto> results) {
        String itemName   = execution.path("item").path("name").asText("unknown");
        long responseTime = execution.path("response").path("responseTime").asLong(0);

        JsonNode assertions = execution.path("assertions");
        if (!assertions.isArray() || assertions.isEmpty()) {
            // No assertions — count the request itself as passed (HTTP succeeded)
            results.add(TestCaseResultDto.basic(
                    itemName + "#request", itemName, itemName, "request",
                    List.of(), TestStatus.PASSED, responseTime,
                    null, null, 0, List.of()
            ));
            return;
        }

        for (JsonNode assertion : assertions) {
            String assertionName = assertion.path("assertion").asText("unknown");
            String testId        = itemName + "#" + assertionName;
            JsonNode error       = assertion.path("error");

            TestStatus status;
            String failureMessage = null;

            if (!error.isNull() && !error.isMissingNode() && error.isObject()) {
                status = TestStatus.FAILED;
                failureMessage = error.path("message").asText(
                        error.path("name").asText("Assertion failed"));
            } else {
                status = TestStatus.PASSED;
            }

            results.add(TestCaseResultDto.basic(
                    testId, assertionName, itemName, assertionName,
                    List.of(), status, responseTime,
                    failureMessage, null, 0, List.of()
            ));
        }
    }
}
