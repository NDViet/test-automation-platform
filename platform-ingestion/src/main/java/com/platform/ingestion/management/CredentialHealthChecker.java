package com.platform.ingestion.management;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Lightweight reachability check for an integration credential, used by the
 * "Test connection" admin action. Performs a single authenticated GET against a
 * cheap endpoint per integration family.
 */
@Component
public class CredentialHealthChecker {

    private static final Logger log = LoggerFactory.getLogger(CredentialHealthChecker.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();

    public record Result(boolean ok, String message) {}

    /**
     * @param type    IntegrationType name (JIRA_CLOUD, AZURE_DEVOPS_BOARDS, GITHUB_ISSUES, …)
     * @param baseUrl optional base URL override
     * @param params  merged non-secret params + decrypted secret (pat/token/email/owner/repo/…)
     */
    public Result check(String type, String baseUrl, Map<String, String> params) {
        try {
            return switch (type) {
                case "JIRA_CLOUD", "JIRA_SERVER", "JIRA" -> checkJira(baseUrl, params);
                case "AZURE_DEVOPS_BOARDS", "AZURE_DEVOPS_REPOS" -> checkAzure(baseUrl, params);
                case "GITHUB", "GITHUB_ISSUES" -> checkGitHub(baseUrl, params);
                default -> new Result(false, "Test connection not supported for type " + type);
            };
        } catch (Exception e) {
            return new Result(false, "Connection failed: " + e.getMessage());
        }
    }

    private Result checkJira(String baseUrl, Map<String, String> p) throws Exception {
        String email = p.get("email");
        String token = firstNonBlank(p.get("apiToken"), p.get("api_token"), p.get("token"), p.get("pat"));
        if (baseUrl == null || email == null || token == null) {
            return new Result(false, "JIRA requires baseUrl, email and apiToken");
        }
        String auth = "Basic " + b64(email + ":" + token);
        int code = get(trim(baseUrl) + "/rest/api/3/myself", auth);
        return classify(code, "JIRA");
    }

    private Result checkAzure(String baseUrl, Map<String, String> p) throws Exception {
        String org = firstNonBlank(p.get("organization"), p.get("org"));
        String pat = firstNonBlank(p.get("pat"), p.get("token"));
        if (org == null || pat == null) return new Result(false, "Azure requires organization and pat");
        String root = (baseUrl == null || baseUrl.isBlank()) ? "https://dev.azure.com" : trim(baseUrl);
        if (!root.endsWith("/" + org)) root = root + "/" + org;
        String auth = "Basic " + b64(":" + pat);
        int code = get(root + "/_apis/projects?api-version=7.0", auth);
        return classify(code, "Azure DevOps");
    }

    private Result checkGitHub(String baseUrl, Map<String, String> p) throws Exception {
        String pat = firstNonBlank(p.get("pat"), p.get("token"));
        if (pat == null) return new Result(false, "GitHub requires a pat");
        String root = (baseUrl == null || baseUrl.isBlank()) ? "https://api.github.com" : trim(baseUrl);
        String owner = p.get("owner");
        String repo = p.get("repo");
        String repository = firstNonBlank(p.get("repository"), p.get("repoFullName"));
        if ((owner == null || repo == null) && repository != null && repository.contains("/")) {
            String[] parts = repository.split("/", 2);
            owner = parts[0]; repo = parts[1];
        }
        String url = (owner != null && repo != null)
                ? root + "/repos/" + owner + "/" + repo
                : root + "/user";
        int code = getBearer(url, "Bearer " + pat);
        return classify(code, "GitHub");
    }

    private int get(String url, String authHeader) throws Exception {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", authHeader).header("Accept", "application/json")
                .GET().build();
        return http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private int getBearer(String url, String bearer) throws Exception {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", bearer)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET().build();
        return http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private Result classify(int code, String name) {
        if (code >= 200 && code < 300) return new Result(true, name + " connection OK");
        if (code == 401 || code == 403) return new Result(false, name + " authentication failed (HTTP " + code + ")");
        return new Result(false, name + " returned HTTP " + code);
    }

    private static String trim(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }
}
