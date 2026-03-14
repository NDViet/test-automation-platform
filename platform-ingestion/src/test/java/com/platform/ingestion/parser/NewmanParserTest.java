package com.platform.ingestion.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.platform.common.enums.SourceFormat;
import com.platform.common.enums.TestStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewmanParserTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final NewmanParser parser = new NewmanParser(mapper);

    private final ParseContext ctx = new ParseContext(
            "run-newman-001", "team-f", "api-tests",
            "main", "staging", null, null, null, "UNKNOWN", 0, "");

    @Test
    void shouldParseAssertionsAsTestCases() throws Exception {
        byte[] json = new ClassPathResource("samples/newman/report.json").getContentAsByteArray();

        var results = parser.parse(List.of(json), ctx);

        assertThat(results).hasSize(1);
        var result = results.get(0);
        // 2 assertions (POST /payments) + 2 (GET /payments/:id) + 2 (POST /refunds) = 6
        assertThat(result.total()).isEqualTo(6);
        assertThat(result.passed()).isEqualTo(5);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.sourceFormat()).isEqualTo(SourceFormat.NEWMAN);
    }

    @Test
    void shouldBuildTestIdFromItemHashAssertion() throws Exception {
        byte[] json = new ClassPathResource("samples/newman/report.json").getContentAsByteArray();

        var testCase = parser.parse(List.of(json), ctx).get(0).testCases().get(0);

        assertThat(testCase.testId()).isEqualTo("POST /payments - success#Status code is 200");
    }

    @Test
    void shouldExtractFailureMessageFromAssertionError() throws Exception {
        byte[] json = new ClassPathResource("samples/newman/report.json").getContentAsByteArray();

        var failed = parser.parse(List.of(json), ctx).get(0).testCases().stream()
                .filter(t -> t.status() == TestStatus.FAILED)
                .findFirst().orElseThrow();

        assertThat(failed.testId()).contains("POST /refunds - invalid amount");
        assertThat(failed.failureMessage()).contains("expected response to have status code 200");
    }

    @Test
    void shouldUseResponseTimeAsDuration() throws Exception {
        byte[] json = new ClassPathResource("samples/newman/report.json").getContentAsByteArray();

        var first = parser.parse(List.of(json), ctx).get(0).testCases().get(0);

        assertThat(first.durationMs()).isEqualTo(345L); // from response.responseTime
    }
}
