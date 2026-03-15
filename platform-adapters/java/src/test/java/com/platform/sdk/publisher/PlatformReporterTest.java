package com.platform.sdk.publisher;

import com.platform.sdk.config.PlatformConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests the critical NEVER-THROW contract and multipart body construction.
 */
class PlatformReporterTest {

    @TempDir
    Path tempDir;

    private PlatformConfig enabledConfig;
    private PlatformConfig disabledConfig;

    @BeforeEach
    void setUp() throws Exception {
        enabledConfig  = buildConfig(true,  "http://platform", "key123", "team-a", "proj-x");
        disabledConfig = buildConfig(false, null, null, null, null);
    }

    // ── Never-throw contract ─────────────────────────────────────────────────

    @Test
    void shouldNotThrowWhenDisabled() {
        PlatformReporter reporter = new PlatformReporter(disabledConfig);
        assertThatCode(() ->
                reporter.publishResults(tempDir, "JUNIT_XML", "*.xml", "main"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldNotThrowWhenReportDirDoesNotExist() {
        PlatformReporter reporter = new PlatformReporter(enabledConfig);
        Path missing = tempDir.resolve("does-not-exist");
        assertThatCode(() ->
                reporter.publishResults(missing, "JUNIT_XML", "*.xml", "main"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldNotThrowWhenNoFilesMatchGlob() throws IOException {
        Files.writeString(tempDir.resolve("report.html"), "<html/>");
        PlatformReporter reporter = new PlatformReporter(enabledConfig);
        assertThatCode(() ->
                reporter.publishResults(tempDir, "JUNIT_XML", "*.xml", "main"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldNotThrowWhenHttpClientThrows() throws Exception {
        Files.writeString(tempDir.resolve("TEST-foo.xml"), "<testsuite tests='1'/>");

        HttpClient mockClient = mock(HttpClient.class);
        doThrow(new RuntimeException("connection refused")).when(mockClient).send(any(), any());

        PlatformReporter reporter = reporterWithMockClient(enabledConfig, mockClient);
        assertThatCode(() ->
                reporter.publishResults(tempDir, "JUNIT_XML", "*.xml", "main"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldNotThrowOnNon202Response() throws Exception {
        Files.writeString(tempDir.resolve("TEST-foo.xml"), "<testsuite tests='1'/>");

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(500);
        when(mockResponse.body()).thenReturn("Internal Server Error");

        HttpClient mockClient = mock(HttpClient.class);
        doReturn(mockResponse).when(mockClient).send(any(), any());

        PlatformReporter reporter = reporterWithMockClient(enabledConfig, mockClient);
        assertThatCode(() ->
                reporter.publishResults(tempDir, "JUNIT_XML", "*.xml", "main"))
                .doesNotThrowAnyException();
    }

    // ── Successful publish ────────────────────────────────────────────────────

    @Test
    void shouldSendRequestOnSuccessfulPublish() throws Exception {
        Files.writeString(tempDir.resolve("TEST-example.xml"), "<testsuite tests='2'/>");

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(202);
        when(mockResponse.body()).thenReturn("");

        HttpClient mockClient = mock(HttpClient.class);
        doReturn(mockResponse).when(mockClient).send(any(), any());

        PlatformReporter reporter = reporterWithMockClient(enabledConfig, mockClient);
        reporter.publishResults(tempDir, "JUNIT_XML", "*.xml", "feature/test");

        verify(mockClient, times(1)).send(any(), any());
    }

    // ── doPublish multipart ───────────────────────────────────────────────────

    @Test
    void doPublishShouldSendMultipartRequest() throws Exception {
        Path xmlFile = tempDir.resolve("TEST-sample.xml");
        Files.writeString(xmlFile, "<testsuite tests='3'/>");

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(202);
        when(mockResponse.body()).thenReturn("");

        HttpClient mockClient = mock(HttpClient.class);
        doReturn(mockResponse).when(mockClient).send(any(), any());

        PlatformReporter reporter = reporterWithMockClient(enabledConfig, mockClient);
        reporter.doPublish(List.of(xmlFile), "JUNIT_XML", "main");

        verify(mockClient).send(
                argThat(req ->
                        req.headers().firstValue("Content-Type")
                                .map(ct -> ct.startsWith("multipart/form-data"))
                                .orElse(false)
                        && req.headers().firstValue("X-API-Key")
                                .map("key123"::equals)
                                .orElse(false)
                ),
                any()
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PlatformConfig buildConfig(boolean enabled, String url, String apiKey,
                                        String teamId, String projectId) throws Exception {
        // Use reflection to construct PlatformConfig with arbitrary values
        var constructor = PlatformConfig.class.getDeclaredConstructor(
                String.class, String.class, String.class, String.class, String.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(url, apiKey, teamId, projectId, "test", enabled);
    }

    private PlatformReporter reporterWithMockClient(PlatformConfig config, HttpClient mockClient)
            throws Exception {
        PlatformReporter reporter = new PlatformReporter(config);
        Field field = PlatformReporter.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        field.set(reporter, mockClient);
        return reporter;
    }
}
