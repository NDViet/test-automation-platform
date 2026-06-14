package com.platform.integration.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Low-level HTTP wrapper for the GitHub Issues REST API.
 *
 * <p>Base URL: {@code https://api.github.com} (or a GitHub Enterprise base).
 * Auth: {@code Authorization: Bearer {pat}}.</p>
 */
public class GitHubIssuesClient {

    private final String baseUrl;
    private final String owner;
    private final String repo;
    private final String token;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public GitHubIssuesClient(String baseUrl, String owner, String repo, String token,
                              ObjectMapper mapper) {
        String root = (baseUrl == null || baseUrl.isBlank()) ? "https://api.github.com" : baseUrl;
        this.baseUrl = root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
        this.owner   = owner;
        this.repo    = repo;
        this.token   = token;
        this.http    = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.mapper  = mapper;
    }

    public JsonNode createIssue(String jsonBody) {
        return send("POST", "/repos/" + owner + "/" + repo + "/issues", jsonBody);
    }

    public JsonNode addComment(String number, String body) {
        String payload;
        try {
            payload = mapper.writeValueAsString(java.util.Map.of("body", body));
        } catch (Exception e) {
            throw new GitHubApiException("Failed to serialize comment", e);
        }
        return send("POST", "/repos/" + owner + "/" + repo + "/issues/" + number + "/comments", payload);
    }

    public JsonNode setState(String number, String state) {
        String payload;
        try {
            payload = mapper.writeValueAsString(java.util.Map.of("state", state));
        } catch (Exception e) {
            throw new GitHubApiException("Failed to serialize state", e);
        }
        return send("PATCH", "/repos/" + owner + "/" + repo + "/issues/" + number, payload);
    }

    /** Searches issues in this repo by label and state (e.g. open). */
    public JsonNode searchByLabel(String label, String state) {
        String q = "repo:" + owner + "/" + repo + " is:issue state:" + state
                + " label:\"" + label + "\"";
        String path = "/search/issues?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8)
                + "&per_page=5";
        return send("GET", path, null);
    }

    /** Validates connectivity and repo access. */
    public JsonNode getRepo() {
        return send("GET", "/repos/" + owner + "/" + repo, null);
    }

    private JsonNode send(String method, String path, String body) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28");
            if (body != null) {
                b.header("Content-Type", "application/json");
                b.method(method, HttpRequest.BodyPublishers.ofString(body));
            } else {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                throw new GitHubApiException("GitHub " + method + " " + path
                        + " returned " + resp.statusCode() + ": " + resp.body());
            }
            return resp.body().isBlank() ? mapper.createObjectNode() : mapper.readTree(resp.body());
        } catch (GitHubApiException e) {
            throw e;
        } catch (Exception e) {
            throw new GitHubApiException("GitHub " + method + " " + path + " failed: " + e.getMessage(), e);
        }
    }

    public static class GitHubApiException extends RuntimeException {
        public GitHubApiException(String msg) { super(msg); }
        public GitHubApiException(String msg, Throwable cause) { super(msg, cause); }
    }
}
