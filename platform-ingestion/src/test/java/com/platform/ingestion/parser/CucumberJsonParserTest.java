package com.platform.ingestion.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.platform.common.enums.SourceFormat;
import com.platform.common.enums.TestStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CucumberJsonParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final CucumberJsonParser parser = new CucumberJsonParser(objectMapper);

    private final ParseContext ctx = new ParseContext(
            "run-cuke-001", "team-b", "payments",
            "feature/add-payment", "staging", null, null, null, "UNKNOWN", 0, "");

    @Test
    void shouldParseScenarioStatuses() throws Exception {
        byte[] json = new ClassPathResource("samples/cucumber_json/with_failures.json").getContentAsByteArray();

        var results = parser.parse(List.of(json), ctx);

        assertThat(results).hasSize(1);
        var result = results.get(0);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.passed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.sourceFormat()).isEqualTo(SourceFormat.CUCUMBER_JSON);
    }

    @Test
    void shouldBuildTestIdFromFeatureUriAndScenarioName() throws Exception {
        byte[] json = new ClassPathResource("samples/cucumber_json/with_failures.json").getContentAsByteArray();

        var results = parser.parse(List.of(json), ctx);
        var testCases = results.get(0).testCases();

        assertThat(testCases.get(0).testId())
                .isEqualTo("features/payment.feature#Successful payment");
    }

    @Test
    void shouldExtractTagsFromScenario() throws Exception {
        byte[] json = new ClassPathResource("samples/cucumber_json/with_failures.json").getContentAsByteArray();

        var results = parser.parse(List.of(json), ctx);
        var firstScenario = results.get(0).testCases().get(0);

        assertThat(firstScenario.tags()).containsExactlyInAnyOrder("@payment", "@smoke");
    }

    @Test
    void shouldExtractFailureMessageFromFailedStep() throws Exception {
        byte[] json = new ClassPathResource("samples/cucumber_json/with_failures.json").getContentAsByteArray();

        var results = parser.parse(List.of(json), ctx);
        var failedScenario = results.get(0).testCases().stream()
                .filter(t -> t.status() == TestStatus.FAILED)
                .findFirst().orElseThrow();

        assertThat(failedScenario.failureMessage()).contains("Expected status 200");
    }

    @Test
    void shouldComputeDurationFromStepDurations() throws Exception {
        byte[] json = new ClassPathResource("samples/cucumber_json/with_failures.json").getContentAsByteArray();

        var results = parser.parse(List.of(json), ctx);
        var firstScenario = results.get(0).testCases().get(0);

        // 120ms + 80ms = 200ms
        assertThat(firstScenario.durationMs()).isEqualTo(200L);
    }
}
