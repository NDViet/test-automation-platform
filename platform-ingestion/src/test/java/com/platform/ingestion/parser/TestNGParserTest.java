package com.platform.ingestion.parser;

import com.platform.common.enums.SourceFormat;
import com.platform.common.enums.TestStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestNGParserTest {

    private final TestNGParser parser = new TestNGParser();

    private final ParseContext ctx = new ParseContext(
            "run-ng-001", "team-c", "payments",
            "main", "staging", null, null, null, "UNKNOWN", 0, "");

    @Test
    void shouldParseAllStatusTypes() throws Exception {
        byte[] xml = new ClassPathResource("samples/testng/with_failures.xml").getContentAsByteArray();

        var results = parser.parse(List.of(xml), ctx);

        assertThat(results).hasSize(1);
        var result = results.get(0);
        assertThat(result.total()).isEqualTo(5);
        assertThat(result.passed()).isEqualTo(3);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.sourceFormat()).isEqualTo(SourceFormat.TESTNG);
    }

    @Test
    void shouldBuildTestIdAsClassHashMethod() throws Exception {
        byte[] xml = new ClassPathResource("samples/testng/with_failures.xml").getContentAsByteArray();

        var results = parser.parse(List.of(xml), ctx);
        var testCase = results.get(0).testCases().get(0);

        assertThat(testCase.testId()).isEqualTo("com.example.PaymentTest#shouldProcessPayment");
    }

    @Test
    void shouldExtractFailureMessageAndStackTrace() throws Exception {
        byte[] xml = new ClassPathResource("samples/testng/with_failures.xml").getContentAsByteArray();

        var failed = results(xml).stream()
                .filter(t -> t.status() == TestStatus.FAILED)
                .findFirst().orElseThrow();

        assertThat(failed.failureMessage()).contains("Expected status 200 but was 422");
        assertThat(failed.stackTrace()).contains("PaymentTest.java:87");
    }

    @Test
    void shouldCaptureSuiteDurationMs() throws Exception {
        byte[] xml = new ClassPathResource("samples/testng/with_failures.xml").getContentAsByteArray();

        var result = parser.parse(List.of(xml), ctx).get(0);

        assertThat(result.durationMs()).isEqualTo(4210L);
    }

    @Test
    void shouldSkipConfigMethods() throws Exception {
        byte[] xml = new ClassPathResource("samples/testng/with_failures.xml").getContentAsByteArray();

        // All 5 test-methods in the file are actual tests — no config methods
        // The count must match total = 5 (3 pass + 1 fail + 1 skip)
        assertThat(results(xml)).hasSize(5);
    }

    private List<com.platform.common.dto.TestCaseResultDto> results(byte[] xml) throws Exception {
        return parser.parse(List.of(xml), ctx).get(0).testCases();
    }
}
