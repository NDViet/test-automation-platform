package com.platform.ingestion.parser;

import com.platform.common.enums.SourceFormat;
import com.platform.common.enums.TestStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JMeterJtlParserTest {

    private final JMeterJtlParser parser = new JMeterJtlParser();

    private final ParseContext ctx = new ParseContext(
            "run-jmeter-001", "team-perf", "jmeter-tests",
            "main", "perf", null, null, null, "SEQUENTIAL", 1, "WebTestPlan");

    @Test
    void shouldGroupSamplesByLabel() throws Exception {
        byte[] jtl = new ClassPathResource("samples/jmeter/results.jtl").getContentAsByteArray();

        var results = parser.parse(List.of(jtl), ctx);

        assertThat(results).hasSize(1);
        var result = results.get(0);
        // Labels: "Homepage - GET /", "Login - POST /auth/login",
        //         "Search - GET /search?q=test", "Add to Cart - POST /cart" = 4
        assertThat(result.total()).isEqualTo(4);
        assertThat(result.sourceFormat()).isEqualTo(SourceFormat.JMETER);
    }

    @Test
    void shouldMarkLabelPassedWhenAllSamplesSucceed() throws Exception {
        byte[] jtl = new ClassPathResource("samples/jmeter/results.jtl").getContentAsByteArray();

        var testCases = parser.parse(List.of(jtl), ctx).get(0).testCases();

        var homepage = testCases.stream()
                .filter(t -> t.testId().equals("Homepage - GET /"))
                .findFirst().orElseThrow();
        assertThat(homepage.status()).isEqualTo(TestStatus.PASSED);
        assertThat(homepage.failureMessage()).isNull();
    }

    @Test
    void shouldMarkLabelFailedWhenAnySampleFails() throws Exception {
        byte[] jtl = new ClassPathResource("samples/jmeter/results.jtl").getContentAsByteArray();

        var testCases = parser.parse(List.of(jtl), ctx).get(0).testCases();

        // Login has 1 failure (s="false", rc="500")
        var login = testCases.stream()
                .filter(t -> t.testId().equals("Login - POST /auth/login"))
                .findFirst().orElseThrow();
        assertThat(login.status()).isEqualTo(TestStatus.FAILED);
        assertThat(login.failureMessage()).contains("50.0%").contains("500");
    }

    @Test
    void shouldComputeMeanDurationFromAllSamples() throws Exception {
        byte[] jtl = new ClassPathResource("samples/jmeter/results.jtl").getContentAsByteArray();

        var testCases = parser.parse(List.of(jtl), ctx).get(0).testCases();

        // Homepage samples: t=125, t=89, t=67 → mean = (125+89+67)/3 = 93
        var homepage = testCases.stream()
                .filter(t -> t.testId().equals("Homepage - GET /"))
                .findFirst().orElseThrow();
        assertThat(homepage.durationMs()).isEqualTo(93L);
    }

    @Test
    void shouldCountPassedAndFailed() throws Exception {
        byte[] jtl = new ClassPathResource("samples/jmeter/results.jtl").getContentAsByteArray();

        var result = parser.parse(List.of(jtl), ctx).get(0);

        // Homepage (pass), Add to Cart (pass) = 2 passed
        // Login (fail), Search (fail) = 2 failed
        assertThat(result.passed()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(2);
    }

    @Test
    void shouldThrowParseExceptionForEmptySamples() {
        byte[] noSamples = "<?xml version=\"1.0\"?><testResults version=\"1.2\"></testResults>".getBytes();

        assertThatThrownBy(() -> parser.parse(List.of(noSamples), ctx))
                .hasMessageContaining("No samples");
    }

    @Test
    void shouldThrowParseExceptionForEmptyFileList() {
        assertThatThrownBy(() -> parser.parse(List.of(), ctx))
                .hasMessageContaining("No JMeter");
    }

    @Test
    void shouldReturnJMeterSourceFormat() {
        assertThat(parser.supportedFormat()).isEqualTo(SourceFormat.JMETER);
    }
}
