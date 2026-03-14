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

class GatlingStatsJsonParserTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final GatlingStatsJsonParser parser = new GatlingStatsJsonParser(mapper);

    private final ParseContext ctx = new ParseContext(
            "run-gatling-001", "team-perf", "gatling-tests",
            "main", "perf", null, null, null, "SEQUENTIAL", 1, "LoadSimulation");

    @Test
    void shouldParseRequestEntriesAsTestCases() throws Exception {
        byte[] json = new ClassPathResource("samples/gatling/stats.json").getContentAsByteArray();

        var results = parser.parse(List.of(json), ctx);

        assertThat(results).hasSize(1);
        var result = results.get(0);
        // req_homepage (REQUEST) + req_login (REQUEST) + req_add_cart (REQUEST inside GROUP) = 3
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.sourceFormat()).isEqualTo(SourceFormat.GATLING);
    }

    @Test
    void shouldMarkRequestPassedWhenNoErrors() throws Exception {
        byte[] json = new ClassPathResource("samples/gatling/stats.json").getContentAsByteArray();

        var testCases = parser.parse(List.of(json), ctx).get(0).testCases();

        // Homepage has ko=0
        var homepage = testCases.stream()
                .filter(t -> t.displayName().equals("Homepage - GET /"))
                .findFirst().orElseThrow();
        assertThat(homepage.status()).isEqualTo(TestStatus.PASSED);
        assertThat(homepage.failureMessage()).isNull();
    }

    @Test
    void shouldMarkRequestFailedWhenKoGreaterThanZero() throws Exception {
        byte[] json = new ClassPathResource("samples/gatling/stats.json").getContentAsByteArray();

        var testCases = parser.parse(List.of(json), ctx).get(0).testCases();

        // Login has ko=15
        var login = testCases.stream()
                .filter(t -> t.displayName().equals("Login - POST /auth/login"))
                .findFirst().orElseThrow();
        assertThat(login.status()).isEqualTo(TestStatus.FAILED);
        assertThat(login.failureMessage()).contains("10.0%").contains("15/150");
    }

    @Test
    void shouldTraverseNestedGroupContents() throws Exception {
        byte[] json = new ClassPathResource("samples/gatling/stats.json").getContentAsByteArray();

        var testCases = parser.parse(List.of(json), ctx).get(0).testCases();

        // Add to Cart is inside a GROUP — should still be parsed
        var addCart = testCases.stream()
                .filter(t -> t.displayName().equals("Add to Cart - POST /cart"))
                .findFirst().orElseThrow();
        assertThat(addCart.status()).isEqualTo(TestStatus.PASSED);
    }

    @Test
    void shouldUseMeanResponseTimeAsDuration() throws Exception {
        byte[] json = new ClassPathResource("samples/gatling/stats.json").getContentAsByteArray();

        var testCases = parser.parse(List.of(json), ctx).get(0).testCases();

        var homepage = testCases.stream()
                .filter(t -> t.displayName().equals("Homepage - GET /"))
                .findFirst().orElseThrow();
        assertThat(homepage.durationMs()).isEqualTo(85L);
    }

    @Test
    void shouldCountPassedAndFailed() throws Exception {
        byte[] json = new ClassPathResource("samples/gatling/stats.json").getContentAsByteArray();

        var result = parser.parse(List.of(json), ctx).get(0);

        assertThat(result.passed()).isEqualTo(2); // Homepage + Add to Cart
        assertThat(result.failed()).isEqualTo(1); // Login
    }

    @Test
    void shouldThrowParseExceptionForMissingContents() {
        byte[] badJson = "{\"type\":\"GROUP\"}".getBytes();

        assertThatThrownBy(() -> parser.parse(List.of(badJson), ctx))
                .hasMessageContaining("contents");
    }

    @Test
    void shouldReturnGatlingSourceFormat() {
        assertThat(parser.supportedFormat()).isEqualTo(SourceFormat.GATLING);
    }
}
