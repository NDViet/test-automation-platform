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
 * Parses Allure results — each file is a single *-result.json.
 * Multiple files are passed (one per test case).
 */
@Component
public class AllureParser implements ResultParser {

    private static final Logger log = LoggerFactory.getLogger(AllureParser.class);

    private final ObjectMapper objectMapper;

    public AllureParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public SourceFormat supportedFormat() {
        return SourceFormat.ALLURE;
    }

    @Override
    public List<UnifiedTestResult> parse(List<byte[]> files, ParseContext ctx) throws ParseException {
        if (files == null || files.isEmpty()) {
            throw new ParseException("No Allure result files provided", SourceFormat.ALLURE);
        }

        List<TestCaseResultDto> allCases = new ArrayList<>();

        for (byte[] file : files) {
            try {
                JsonNode node = objectMapper.readTree(file);
                // skip container/environment files — they won't have a "name" field
                if (!node.has("name") || !node.has("status")) continue;
                allCases.add(parseResult(node));
            } catch (ParseException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Skipping unparseable Allure result file: {}", e.getMessage());
            }
        }

        if (allCases.isEmpty()) {
            throw new ParseException("No valid Allure result files found", SourceFormat.ALLURE);
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
                SourceFormat.ALLURE, allCases,
                ctx.executionMode(), ctx.parallelism(), ctx.suiteName()
        ));
    }

    private TestCaseResultDto parseResult(JsonNode node) {
        String fullName = node.path("fullName").asText(node.path("name").asText("unknown"));
        String name     = node.path("name").asText("unknown");

        long start = node.path("start").asLong(0);
        long stop  = node.path("stop").asLong(0);
        long durationMs = stop > start ? stop - start : 0;

        // Tags from labels array (feature, story, epic, tag)
        List<String> tags = new ArrayList<>();
        node.path("labels").forEach(label -> {
            String labelName  = label.path("name").asText();
            String labelValue = label.path("value").asText();
            if (!labelValue.isBlank()) tags.add(labelName + ":" + labelValue);
        });

        TestStatus status = mapStatus(node.path("status").asText("unknown"));

        String failureMessage = null;
        String stackTrace = null;
        JsonNode details = node.path("statusDetails");
        if (!details.isMissingNode()) {
            String msg = details.path("message").asText(null);
            String trace = details.path("trace").asText(null);
            if (msg != null && !msg.isBlank()) failureMessage = msg;
            if (trace != null && !trace.isBlank()) stackTrace = trace;
        }

        // Derive className and methodName from fullName (com.example.Class#method)
        String className  = fullName.contains("#") ? fullName.substring(0, fullName.indexOf('#')) : fullName;
        String methodName = fullName.contains("#") ? fullName.substring(fullName.indexOf('#') + 1) : name;

        return TestCaseResultDto.basic(
                fullName, name, className, methodName,
                tags, status, durationMs,
                failureMessage, stackTrace, 0, List.of()
        );
    }

    private TestStatus mapStatus(String raw) {
        return switch (raw.toLowerCase()) {
            case "passed"  -> TestStatus.PASSED;
            case "failed"  -> TestStatus.FAILED;
            case "skipped", "pending" -> TestStatus.SKIPPED;
            case "broken"  -> TestStatus.BROKEN;
            default        -> TestStatus.BROKEN;
        };
    }
}
