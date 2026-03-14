package com.platform.ingestion.parser;

import com.platform.common.enums.SourceFormat;
import com.platform.common.enums.TestStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JUnitXmlParserTest {

    private final JUnitXmlParser parser = new JUnitXmlParser();

    private final ParseContext ctx = new ParseContext(
            "run-test-001", "team-a", "checkout",
            "main", "staging", null, null, null, "UNKNOWN", 0, "");

    @Test
    void shouldParseAllPassingTests() throws Exception {
        byte[] xml = new ClassPathResource("samples/junit_xml/passing.xml").getContentAsByteArray();

        var results = parser.parse(List.of(xml), ctx);

        assertThat(results).hasSize(1);
        var result = results.get(0);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.passed()).isEqualTo(3);
        assertThat(result.failed()).isEqualTo(0);
        assertThat(result.skipped()).isEqualTo(0);
        assertThat(result.broken()).isEqualTo(0);
        assertThat(result.runId()).isEqualTo("run-test-001");
        assertThat(result.sourceFormat()).isEqualTo(SourceFormat.JUNIT_XML);
    }

    @Test
    void shouldParseFailedSkippedAndBrokenTests() throws Exception {
        byte[] xml = new ClassPathResource("samples/junit_xml/with_failures.xml").getContentAsByteArray();

        var results = parser.parse(List.of(xml), ctx);

        assertThat(results).hasSize(1);
        var result = results.get(0);
        assertThat(result.total()).isEqualTo(4);
        assertThat(result.passed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.broken()).isEqualTo(1);
    }

    @Test
    void shouldExtractTestIdAsClassHashMethod() throws Exception {
        byte[] xml = new ClassPathResource("samples/junit_xml/passing.xml").getContentAsByteArray();

        var results = parser.parse(List.of(xml), ctx);
        var testCases = results.get(0).testCases();

        assertThat(testCases.get(0).testId())
                .isEqualTo("com.example.PaymentServiceTest#shouldProcessPayment");
    }

    @Test
    void shouldExtractFailureMessageAndStackTrace() throws Exception {
        byte[] xml = new ClassPathResource("samples/junit_xml/with_failures.xml").getContentAsByteArray();

        var results = parser.parse(List.of(xml), ctx);
        var failedTest = results.get(0).testCases().stream()
                .filter(t -> t.status() == TestStatus.FAILED)
                .findFirst().orElseThrow();

        assertThat(failedTest.failureMessage()).contains("Expected 10.00");
        assertThat(failedTest.stackTrace()).contains("AssertionError");
    }

    @Test
    void shouldCombineMultipleXmlFilesIntoOneResult() throws Exception {
        byte[] xml1 = new ClassPathResource("samples/junit_xml/passing.xml").getContentAsByteArray();
        byte[] xml2 = new ClassPathResource("samples/junit_xml/with_failures.xml").getContentAsByteArray();

        var results = parser.parse(List.of(xml1, xml2), ctx);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).total()).isEqualTo(7);
    }

    @Test
    void shouldThrowParseExceptionForEmptyFileList() {
        assertThatThrownBy(() -> parser.parse(List.of(), ctx))
                .isInstanceOf(com.platform.ingestion.exception.ParseException.class);
    }
}
