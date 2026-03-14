package com.platform.ai.classification;

import com.platform.core.domain.TestCaseResult;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds token-efficient prompts for AI failure classification.
 *
 * <p>Design goals:
 * <ul>
 *   <li>Compact system prompt — one-line category definitions instead of verbose paragraphs</li>
 *   <li>Stack trace capped at {@value #MAX_STACK_LINES} lines (key frames only)</li>
 *   <li>History limited to {@value #MAX_HISTORY_RUNS} recent runs in a compact format</li>
 *   <li>No padding or filler text — every token carries signal</li>
 * </ul>
 */
@Component
public class PromptBuilder {

    private static final int MAX_STACK_LINES  = 20;
    private static final int MAX_HISTORY_RUNS = 5;
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneOffset.UTC);

    /**
     * Compact system prompt. Explicit JSON schema + one-line category descriptions.
     * ~200 tokens vs ~350 for the original.
     */
    public static final String SYSTEM_PROMPT =
            "You are a test automation expert. Analyse the test failure and reply with ONLY valid JSON:\n" +
            "{\"category\":\"...\",\"confidence\":0.0,\"rootCause\":\"...\",\"detailedAnalysis\":\"...\"," +
            "\"suggestedFix\":\"...\",\"isFlakyCandidate\":false,\"affectedComponent\":\"...\"}\n" +
            "Categories (pick one):\n" +
            "APPLICATION_BUG=defect in app code | TEST_DEFECT=bad assertion/selector/data | " +
            "ENVIRONMENT=infra/CI/config issue | FLAKY_TIMING=race condition/async | " +
            "DEPENDENCY=external service/DB unavailable | UNKNOWN=insufficient info\n" +
            "Rules: confidence 0-1; rootCause <=120 chars; suggestedFix actionable; no markdown.";

    public String buildUserPrompt(String testId,
                                  String failureMessage,
                                  String stackTrace,
                                  List<TestCaseResult> history) {
        StringBuilder sb = new StringBuilder(512);

        sb.append("Test: ").append(testId).append('\n');

        if (failureMessage != null && !failureMessage.isBlank()) {
            sb.append("Error: ").append(failureMessage.trim()).append('\n');
        }

        if (stackTrace != null && !stackTrace.isBlank()) {
            sb.append("Stack (top ").append(MAX_STACK_LINES).append(" lines):\n");
            sb.append(truncate(stackTrace, MAX_STACK_LINES)).append('\n');
        }

        if (!history.isEmpty()) {
            int limit = Math.min(history.size(), MAX_HISTORY_RUNS);
            sb.append("Recent ").append(limit).append(" runs: ");
            for (int i = 0; i < limit; i++) {
                TestCaseResult r = history.get(i);
                String ts  = r.getCreatedAt() != null ? FMT.format(r.getCreatedAt()) : "?";
                String env = r.getExecution() != null && r.getExecution().getEnvironment() != null
                        ? r.getExecution().getEnvironment() : "?";
                sb.append('[').append(ts).append(' ').append(r.getStatus())
                  .append('/').append(env).append(']');
                if (i < limit - 1) sb.append(' ');
            }
            sb.append('\n');
        }

        sb.append("Reply JSON only.");
        return sb.toString();
    }

    private String truncate(String text, int maxLines) {
        String[] lines = text.split("\n");
        if (lines.length <= maxLines) return text;
        return Arrays.stream(lines, 0, maxLines)
                .collect(Collectors.joining("\n")) + "\n...(truncated)";
    }
}
