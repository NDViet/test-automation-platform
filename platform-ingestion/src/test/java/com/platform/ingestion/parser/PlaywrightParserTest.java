package com.platform.ingestion.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.platform.common.enums.SourceFormat;
import com.platform.common.enums.TestStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlaywrightParserTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final PlaywrightParser parser = new PlaywrightParser(mapper);

    private final ParseContext ctx = new ParseContext(
            "run-pw-001", "team-e", "search",
            "main", "staging", null, null, null, "UNKNOWN", 0, "");

    @Test
    void shouldParseAllStatuses() throws Exception {
        byte[] json = new ClassPathResource("samples/playwright/results.json").getContentAsByteArray();

        var results = parser.parse(List.of(json), ctx);

        assertThat(results).hasSize(1);
        var result = results.get(0);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.passed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.sourceFormat()).isEqualTo(SourceFormat.PLAYWRIGHT);
    }

    @Test
    void shouldBuildTestIdFromFileAndSpecTitle() throws Exception {
        byte[] json = new ClassPathResource("samples/playwright/results.json").getContentAsByteArray();

        var testCase = parser.parse(List.of(json), ctx).get(0).testCases().get(0);

        assertThat(testCase.testId()).isEqualTo("tests/payment.spec.ts#should complete checkout");
    }

    @Test
    void shouldExtractErrorMessageFromFailedSpec() throws Exception {
        byte[] json = new ClassPathResource("samples/playwright/results.json").getContentAsByteArray();

        var failed = parser.parse(List.of(json), ctx).get(0).testCases().stream()
                .filter(t -> t.status() == TestStatus.FAILED)
                .findFirst().orElseThrow();

        assertThat(failed.failureMessage()).contains("Expected: 90");
        assertThat(failed.stackTrace()).contains("payment.spec.ts:34");
    }

    @Test
    void shouldAccumulateDurationFromResults() throws Exception {
        byte[] json = new ClassPathResource("samples/playwright/results.json").getContentAsByteArray();

        var passed = parser.parse(List.of(json), ctx).get(0).testCases().stream()
                .filter(t -> t.status() == TestStatus.PASSED)
                .findFirst().orElseThrow();

        assertThat(passed.durationMs()).isEqualTo(1240L);
    }
}
