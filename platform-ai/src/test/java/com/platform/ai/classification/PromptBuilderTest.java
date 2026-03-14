package com.platform.ai.classification;

import com.platform.common.enums.TestStatus;
import com.platform.core.domain.TestCaseResult;
import com.platform.core.domain.TestExecution;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();

    @Test
    void includesTestIdAndFailureMessage() {
        String prompt = builder.buildUserPrompt(
                "com.example.LoginTest#login",
                "Expected 200 but got 500",
                null,
                List.of());

        assertThat(prompt).contains("com.example.LoginTest#login");
        assertThat(prompt).contains("Expected 200 but got 500");
    }

    @Test
    void truncatesStackTraceAt20Lines() {
        String longTrace = "line\n".repeat(100);
        String prompt = builder.buildUserPrompt("test#m", "fail", longTrace, List.of());

        assertThat(prompt).contains("(truncated)");
        long lineCount = prompt.lines()
                .filter(l -> l.equals("line"))
                .count();
        assertThat(lineCount).isLessThanOrEqualTo(20);
    }

    @Test
    void includesRunHistory() {
        List<TestCaseResult> history = List.of(
                makeResult(TestStatus.FAILED, "staging"),
                makeResult(TestStatus.PASSED, "staging"));

        String prompt = builder.buildUserPrompt("test#m", "fail", null, history);

        assertThat(prompt).contains("Recent");
        assertThat(prompt).contains("FAILED");
        assertThat(prompt).contains("PASSED");
        assertThat(prompt).contains("staging");
    }

    @Test
    void omitsStackTraceSectionWhenNull() {
        String prompt = builder.buildUserPrompt("test#m", "msg", null, List.of());
        assertThat(prompt).doesNotContain("Stack");
    }

    @Test
    void omitsHistorySectionWhenEmpty() {
        String prompt = builder.buildUserPrompt("test#m", "msg", null, List.of());
        assertThat(prompt).doesNotContain("runs:");
    }

    @Test
    void systemPromptContainsAllCategories() {
        for (FailureCategory cat : FailureCategory.values()) {
            assertThat(PromptBuilder.SYSTEM_PROMPT).contains(cat.name());
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private TestCaseResult makeResult(TestStatus status, String env) {
        TestExecution exec = new TestExecution.Builder()
                .runId(UUID.randomUUID().toString())
                .environment(env)
                .totalTests(1)
                .passed(status == TestStatus.PASSED ? 1 : 0)
                .failed(status == TestStatus.FAILED ? 1 : 0)
                .broken(0).skipped(0)
                .executedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        return TestCaseResult.builder()
                .execution(exec)
                .testId("test#m")
                .status(status)
                .build();
    }
}
