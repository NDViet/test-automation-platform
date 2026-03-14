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

class AllureParserTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final AllureParser parser = new AllureParser(mapper);

    private final ParseContext ctx = new ParseContext(
            "run-allure-001", "team-d", "auth",
            "main", "staging", null, null, null, "UNKNOWN", 0, "");

    @Test
    void shouldParseMultipleResultFiles() throws Exception {
        byte[] pass = new ClassPathResource("samples/allure/pass-result.json").getContentAsByteArray();
        byte[] fail = new ClassPathResource("samples/allure/fail-result.json").getContentAsByteArray();
        byte[] skip = new ClassPathResource("samples/allure/skip-result.json").getContentAsByteArray();

        var results = parser.parse(List.of(pass, fail, skip), ctx);

        assertThat(results).hasSize(1);
        var result = results.get(0);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.passed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.sourceFormat()).isEqualTo(SourceFormat.ALLURE);
    }

    @Test
    void shouldUseFullNameAsTestId() throws Exception {
        byte[] pass = new ClassPathResource("samples/allure/pass-result.json").getContentAsByteArray();

        var testCase = parser.parse(List.of(pass), ctx).get(0).testCases().get(0);

        assertThat(testCase.testId()).isEqualTo("com.example.PaymentTest#shouldProcessPayment");
    }

    @Test
    void shouldExtractLabelsAsTags() throws Exception {
        byte[] pass = new ClassPathResource("samples/allure/pass-result.json").getContentAsByteArray();

        var testCase = parser.parse(List.of(pass), ctx).get(0).testCases().get(0);

        assertThat(testCase.tags()).contains("feature:Payment", "story:PROJ-100");
    }

    @Test
    void shouldExtractFailureDetails() throws Exception {
        byte[] fail = new ClassPathResource("samples/allure/fail-result.json").getContentAsByteArray();

        var testCase = parser.parse(List.of(fail), ctx).get(0).testCases().get(0);

        assertThat(testCase.status()).isEqualTo(TestStatus.FAILED);
        assertThat(testCase.failureMessage()).contains("Expected status 200");
        assertThat(testCase.stackTrace()).contains("PaymentTest.java:87");
    }

    @Test
    void shouldComputeDurationFromStartStop() throws Exception {
        byte[] pass = new ClassPathResource("samples/allure/pass-result.json").getContentAsByteArray();

        var testCase = parser.parse(List.of(pass), ctx).get(0).testCases().get(0);

        assertThat(testCase.durationMs()).isEqualTo(1200L); // stop - start = 1200ms
    }

    @Test
    void shouldThrowWhenNoValidFiles() {
        byte[] garbage = "{}".getBytes();
        assertThatThrownBy(() -> parser.parse(List.of(garbage), ctx))
                .isInstanceOf(com.platform.ingestion.exception.ParseException.class);
    }
}
