package com.platform.ingestion.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.platform.common.enums.SourceFormat;
import com.platform.common.enums.TestStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class K6JsonParserTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final K6JsonParser parser = new K6JsonParser(mapper);

    private final ParseContext ctx = new ParseContext(
            "run-k6-001", "team-perf", "load-tests",
            "main", "staging", null, null, null, "PARALLEL", 10, "load-test");

    @Test
    void shouldParseChecksFromRootGroupAndNestedGroups() throws Exception {
        byte[] json = new ClassPathResource("samples/k6/summary.json").getContentAsByteArray();

        var results = parser.parse(List.of(json), ctx);

        assertThat(results).hasSize(1);
        var result = results.get(0);
        // root: 1 check + Homepage: 2 checks + Login: 1 check = 4 total
        assertThat(result.total()).isEqualTo(4);
        assertThat(result.sourceFormat()).isEqualTo(SourceFormat.K6);
    }

    @Test
    void shouldMarkCheckPassedWhenFailsIsZero() throws Exception {
        byte[] json = new ClassPathResource("samples/k6/summary.json").getContentAsByteArray();

        var testCases = parser.parse(List.of(json), ctx).get(0).testCases();

        // "health check passed" — passes=10, fails=0
        var healthCheck = testCases.stream()
                .filter(t -> t.displayName().equals("health check passed"))
                .findFirst().orElseThrow();
        assertThat(healthCheck.status()).isEqualTo(TestStatus.PASSED);
        assertThat(healthCheck.failureMessage()).isNull();
    }

    @Test
    void shouldMarkCheckFailedWhenPartialFailure() throws Exception {
        byte[] json = new ClassPathResource("samples/k6/summary.json").getContentAsByteArray();

        var testCases = parser.parse(List.of(json), ctx).get(0).testCases();

        // "response time < 500ms" — passes=95, fails=5
        var responseTimeCheck = testCases.stream()
                .filter(t -> t.displayName().equals("response time < 500ms"))
                .findFirst().orElseThrow();
        assertThat(responseTimeCheck.status()).isEqualTo(TestStatus.FAILED);
        assertThat(responseTimeCheck.failureMessage()).contains("5.0%");
    }

    @Test
    void shouldCountPassedAndFailed() throws Exception {
        byte[] json = new ClassPathResource("samples/k6/summary.json").getContentAsByteArray();

        var result = parser.parse(List.of(json), ctx).get(0);

        // "health check passed" + "status is 200" (Homepage) + "status is 200" (Login) = 3 passed
        // "response time < 500ms" = 1 failed
        assertThat(result.passed()).isEqualTo(3);
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    void shouldBuildTestIdIncludingGroupPath() throws Exception {
        byte[] json = new ClassPathResource("samples/k6/summary.json").getContentAsByteArray();

        var testCases = parser.parse(List.of(json), ctx).get(0).testCases();

        // Nested check should include group path in ID
        var nestedCheck = testCases.stream()
                .filter(t -> t.testId().contains("Homepage"))
                .findFirst().orElseThrow();
        assertThat(nestedCheck.testId()).contains("Homepage");
    }

    @Test
    void shouldThrowParseExceptionForMissingRootGroup() {
        byte[] badJson = "{}".getBytes();

        assertThatThrownBy(() -> parser.parse(List.of(badJson), ctx))
                .hasMessageContaining("root_group");
    }

    @Test
    void shouldThrowParseExceptionForEmptyFileList() {
        assertThatThrownBy(() -> parser.parse(List.of(), ctx))
                .hasMessageContaining("No k6");
    }

    @Test
    void shouldReturnK6SourceFormat() {
        assertThat(parser.supportedFormat()).isEqualTo(SourceFormat.K6);
    }
}
