package com.platform.sdk.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.platform.common.dto.UnifiedTestResult;
import com.platform.sdk.config.PlatformConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * HTTP publisher shared by all framework adapters.
 * <p>
 * CRITICAL CONTRACT: this class NEVER throws — any failure is logged at WARN
 * and silently swallowed so that test execution is never affected by platform issues.
 */
public class PlatformReporter {

    private static final Logger log = LoggerFactory.getLogger(PlatformReporter.class);
    private static final int TIMEOUT_SECONDS = 30;

    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final PlatformConfig config;
    private final HttpClient httpClient;

    public PlatformReporter(PlatformConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Publishes results from {@code reportDir} to the platform.
     *
     * @param reportDir  directory containing report files
     * @param format     format name (JUNIT_XML, CUCUMBER_JSON, TESTNG, etc.)
     * @param fileGlob   glob pattern to match files in {@code reportDir}
     * @param branch     current git branch (from CI env var)
     */
    public void publishResults(Path reportDir, String format, String fileGlob, String branch) {
        if (!config.isEnabled()) {
            log.debug("[Platform SDK] Disabled — skipping publication");
            return;
        }
        if (!config.isValid()) {
            log.warn("[Platform SDK] Missing required config (url/apiKey/teamId/projectId) — skipping");
            return;
        }

        try {
            List<Path> files = collectFiles(reportDir, fileGlob);
            if (files.isEmpty()) {
                log.warn("[Platform SDK] No {} files found in {} — skipping", format, reportDir);
                return;
            }
            doPublish(files, format, branch);
        } catch (Exception e) {
            log.warn("[Platform SDK] Failed to publish results (non-fatal): {}", e.getMessage());
        }
    }

    /**
     * Publishes a natively-produced {@link UnifiedTestResult} (from platform-testkit-java)
     * directly as JSON without needing intermediate report files.
     *
     * <p>NEVER throws — any failure is logged at WARN and swallowed.</p>
     */
    public void publishNative(UnifiedTestResult result) {
        if (!config.isEnabled()) {
            log.debug("[Platform SDK] Disabled — skipping native publication");
            return;
        }
        if (!config.isValid()) {
            log.warn("[Platform SDK] Missing required config — skipping native publication");
            return;
        }
        try {
            byte[] json = JSON.writeValueAsBytes(result);
            String boundary = "----PlatformSDKNative" + UUID.randomUUID().toString().replace("-", "");
            byte[] body = buildNativeMultipartBody(json, boundary);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getUrl() + "/api/v1/results/ingest"))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .header("X-API-Key", config.getApiKey())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 202) {
                log.info("[Platform SDK] Native result published — runId={} tests={}",
                        result.runId(), result.testCases().size());
            } else {
                log.warn("[Platform SDK] Platform returned HTTP {} for native publish — {}",
                        response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("[Platform SDK] Failed to publish native result (non-fatal): {}", e.getMessage());
        }
    }

    private byte[] buildNativeMultipartBody(byte[] json, String boundary) {
        String nl   = "\r\n";
        String dash = "--";

        StringBuilder header = new StringBuilder();
        for (String[] field : new String[][]{
                {"teamId", config.getTeamId()},
                {"projectId", config.getProjectId()},
                {"format", "PLATFORM_NATIVE"},
                {"environment", config.getEnvironment()}
        }) {
            header.append(dash).append(boundary).append(nl)
                  .append("Content-Disposition: form-data; name=\"").append(field[0]).append("\"").append(nl)
                  .append(nl).append(field[1]).append(nl);
        }

        String fileHeader = dash + boundary + nl
                + "Content-Disposition: form-data; name=\"files\"; filename=\"native-result.json\"" + nl
                + "Content-Type: application/json" + nl + nl;
        String closing = nl + dash + boundary + dash + nl;

        byte[] h  = header.toString().getBytes();
        byte[] fh = fileHeader.getBytes();
        byte[] cl = closing.getBytes();
        byte[] result = new byte[h.length + fh.length + json.length + cl.length];
        int off = 0;
        System.arraycopy(h,  0, result, off, h.length);  off += h.length;
        System.arraycopy(fh, 0, result, off, fh.length); off += fh.length;
        System.arraycopy(json, 0, result, off, json.length); off += json.length;
        System.arraycopy(cl, 0, result, off, cl.length);
        return result;
    }

    private List<Path> collectFiles(Path dir, String glob) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        List<Path> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(dir, 2)) {
            stream.filter(p -> matchesGlob(p.getFileName().toString(), glob))
                    .forEach(result::add);
        }
        return result;
    }

    private boolean matchesGlob(String filename, String glob) {
        // Convert glob to simple regex: *.xml → .*\.xml
        String regex = glob.replace(".", "\\.").replace("*", ".*");
        return filename.matches(regex);
    }

    void doPublish(List<Path> files, String format, String branch) throws Exception {
        String boundary = "----PlatformSDK" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = buildMultipartBody(files, format, branch, boundary);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getUrl() + "/api/v1/results/ingest"))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("X-API-Key", config.getApiKey())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 202) {
            log.info("[Platform SDK] Results published — format={} team={} project={} branch={}",
                    format, config.getTeamId(), config.getProjectId(), branch);
        } else {
            log.warn("[Platform SDK] Platform returned HTTP {} — body: {}",
                    response.statusCode(), response.body());
        }
    }

    private byte[] buildMultipartBody(List<Path> files, String format,
                                       String branch, String boundary) throws IOException {
        StringBuilder sb = new StringBuilder();
        String nl   = "\r\n";
        String dash = "--";

        // Form fields
        appendField(sb, boundary, "teamId",      config.getTeamId());
        appendField(sb, boundary, "projectId",   config.getProjectId());
        appendField(sb, boundary, "format",      format);
        appendField(sb, boundary, "environment", config.getEnvironment());
        if (branch != null && !branch.isBlank()) {
            appendField(sb, boundary, "branch", branch);
        }

        // Detect CI run URL
        String ciRunUrl = System.getenv("GITHUB_SERVER_URL") != null
                ? System.getenv("GITHUB_SERVER_URL") + "/" + System.getenv("GITHUB_REPOSITORY")
                  + "/actions/runs/" + System.getenv("GITHUB_RUN_ID")
                : null;
        if (ciRunUrl != null) appendField(sb, boundary, "ciRunUrl", ciRunUrl);

        byte[] headerBytes = sb.toString().getBytes();
        List<byte[]> parts = new ArrayList<>();
        parts.add(headerBytes);

        // File parts
        for (Path file : files) {
            String fileHeader = dash + boundary + nl
                    + "Content-Disposition: form-data; name=\"files\"; filename=\""
                    + file.getFileName() + "\"" + nl
                    + "Content-Type: application/octet-stream" + nl + nl;
            parts.add(fileHeader.getBytes());
            parts.add(Files.readAllBytes(file));
            parts.add(nl.getBytes());
        }
        parts.add((dash + boundary + dash + nl).getBytes());

        int total = parts.stream().mapToInt(b -> b.length).sum();
        byte[] result = new byte[total];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    private void appendField(StringBuilder sb, String boundary, String name, String value) {
        String nl = "\r\n";
        sb.append("--").append(boundary).append(nl)
          .append("Content-Disposition: form-data; name=\"").append(name).append("\"").append(nl)
          .append(nl)
          .append(value).append(nl);
    }
}
