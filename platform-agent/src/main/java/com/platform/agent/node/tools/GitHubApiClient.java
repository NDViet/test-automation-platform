package com.platform.agent.node.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin wrapper around the GitHub REST API v3. Uses RestClient (Spring 6.1+). All methods return raw
 * JSON strings. If a per-call token is blank, falls back to the configured default token.
 */
@Component
public class GitHubApiClient {

  private static final Logger log = LoggerFactory.getLogger(GitHubApiClient.class);
  private static final String GITHUB_API_BASE = "https://api.github.com";

  private final String defaultToken;
  private final RestClient restClient;
  private final ObjectMapper mapper;

  public GitHubApiClient(
      @Value("${platform.agent.github.token:}") String defaultToken, ObjectMapper mapper) {
    this.defaultToken = defaultToken;
    this.mapper = mapper;
    this.restClient =
        RestClient.builder()
            .baseUrl(GITHUB_API_BASE)
            .defaultHeader("Accept", "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .build();
  }

  /**
   * GET /repos/{owner}/{repo}/pulls/{prNumber}/files Returns a JSON array of changed files:
   * [{filename, status, additions, deletions, changes}].
   *
   * @param owner repo owner (org or user)
   * @param repo repo name
   * @param prNumber PR number
   * @param token GitHub token; if blank, falls back to defaultToken
   * @return raw JSON string
   * @throws RuntimeException on HTTP error
   */
  public String getPrFiles(String owner, String repo, int prNumber, String token) {
    String bearerToken = resolveToken(token);
    try {
      log.debug("GitHub API: GET /repos/{}/{}/pulls/{}/files", owner, repo, prNumber);
      return restClient
          .get()
          .uri("/repos/{owner}/{repo}/pulls/{prNumber}/files", owner, repo, prNumber)
          .header("Authorization", "Bearer " + bearerToken)
          .retrieve()
          .body(String.class);
    } catch (Exception e) {
      String msg =
          String.format(
              "Failed to get PR files for %s/%s#%d: %s", owner, repo, prNumber, e.getMessage());
      log.error(msg, e);
      throw new RuntimeException(msg, e);
    }
  }

  /**
   * POST /repos/{owner}/{repo}/issues/{prNumber}/comments Posts a comment on the PR (GitHub uses
   * the issues endpoint for PR comments).
   *
   * @param owner repo owner
   * @param repo repo name
   * @param prNumber PR number
   * @param commentBody markdown comment body
   * @param token GitHub token; if blank, falls back to defaultToken
   * @return raw JSON string of the created comment
   * @throws RuntimeException on HTTP error
   */
  public String postIssueComment(
      String owner, String repo, int prNumber, String commentBody, String token) {
    String bearerToken = resolveToken(token);
    String requestBody = "{\"body\":" + jsonEscape(commentBody) + "}";
    try {
      log.debug("GitHub API: POST /repos/{}/{}/issues/{}/comments", owner, repo, prNumber);
      return restClient
          .post()
          .uri("/repos/{owner}/{repo}/issues/{prNumber}/comments", owner, repo, prNumber)
          .header("Authorization", "Bearer " + bearerToken)
          .header("Content-Type", "application/json")
          .body(requestBody)
          .retrieve()
          .body(String.class);
    } catch (Exception e) {
      String msg =
          String.format(
              "Failed to post comment on %s/%s#%d: %s", owner, repo, prNumber, e.getMessage());
      log.error(msg, e);
      throw new RuntimeException(msg, e);
    }
  }

  /**
   * GET /repos/{owner}/{repo}/pulls?state=open&sort=updated&direction=desc&per_page=100 Returns a
   * JSON array of open PRs sorted by most-recently-updated first. Callers filter by updated_at
   * client-side against their lastPolledAt timestamp.
   *
   * @param owner repo owner
   * @param repo repo name
   * @param token GitHub token; if blank, falls back to defaultToken
   * @return raw JSON string (array)
   */
  public String getOpenPRs(String owner, String repo, String token) {
    String bearerToken = resolveToken(token);
    try {
      log.debug("GitHub API: GET /repos/{}/{}/pulls?state=open&sort=updated", owner, repo);
      return restClient
          .get()
          .uri(
              "/repos/{owner}/{repo}/pulls?state=open&sort=updated&direction=desc&per_page=100",
              owner,
              repo)
          .header("Authorization", "Bearer " + bearerToken)
          .retrieve()
          .body(String.class);
    } catch (Exception e) {
      String msg =
          String.format("Failed to list open PRs for %s/%s: %s", owner, repo, e.getMessage());
      log.error(msg, e);
      throw new RuntimeException(msg, e);
    }
  }

  /**
   * GET /repos/{owner}/{repo}/contents/{path}?ref={ref} Returns the raw JSON from GitHub, which
   * includes a base64-encoded {@code content} field and the {@code sha} required for subsequent
   * update calls.
   *
   * @param owner repo owner
   * @param repo repo name
   * @param path file path within the repository (e.g. "src/test/java/com/example/FooTest.java")
   * @param ref branch name or commit SHA; pass empty string to use the repo default branch
   * @param token GitHub token; if blank, falls back to defaultToken
   * @return raw JSON string from the GitHub Contents API
   */
  public String getFileContent(String owner, String repo, String path, String ref, String token) {
    String bearerToken = resolveToken(token);
    try {
      log.debug("GitHub API: GET /repos/{}/{}/contents/{} ref={}", owner, repo, path, ref);
      String uri =
          (ref == null || ref.isBlank())
              ? "/repos/{owner}/{repo}/contents/{path}"
              : "/repos/{owner}/{repo}/contents/{path}?ref={ref}";
      RestClient.RequestHeadersSpec<?> spec =
          (ref == null || ref.isBlank())
              ? restClient.get().uri(uri, owner, repo, path)
              : restClient.get().uri(uri, owner, repo, path, ref);
      return spec.header("Authorization", "Bearer " + bearerToken).retrieve().body(String.class);
    } catch (Exception e) {
      String msg =
          String.format(
              "Failed to get file content for %s/%s/%s@%s: %s",
              owner, repo, path, ref, e.getMessage());
      log.error(msg, e);
      throw new RuntimeException(msg, e);
    }
  }

  /**
   * PUT /repos/{owner}/{repo}/contents/{path} Creates or updates a file in the repository.
   *
   * @param owner repo owner
   * @param repo repo name
   * @param path file path within the repository
   * @param message commit message
   * @param contentBase64 file content already encoded as Base64
   * @param sha the blob SHA of the file being replaced (required for updates; null for creates)
   * @param branch branch to commit to
   * @param token GitHub token; if blank, falls back to defaultToken
   * @return raw JSON string of the commit response
   */
  public String createOrUpdateFile(
      String owner,
      String repo,
      String path,
      String message,
      String contentBase64,
      String sha,
      String branch,
      String token) {
    String bearerToken = resolveToken(token);
    try {
      ObjectNode body = mapper.createObjectNode();
      body.put("message", message);
      body.put("content", contentBase64);
      body.put("branch", branch);
      if (sha != null && !sha.isBlank()) {
        body.put("sha", sha);
      }
      String requestBody = mapper.writeValueAsString(body);

      log.debug("GitHub API: PUT /repos/{}/{}/contents/{} branch={}", owner, repo, path, branch);
      return restClient
          .put()
          .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, path)
          .header("Authorization", "Bearer " + bearerToken)
          .header("Content-Type", "application/json")
          .body(requestBody)
          .retrieve()
          .body(String.class);
    } catch (Exception e) {
      String msg =
          String.format(
              "Failed to create/update file %s/%s/%s: %s", owner, repo, path, e.getMessage());
      log.error(msg, e);
      throw new RuntimeException(msg, e);
    }
  }

  /**
   * POST /repos/{owner}/{repo}/pulls Creates a pull request (always as a draft for agent-generated
   * PRs).
   *
   * @param owner repo owner
   * @param repo repo name
   * @param title PR title
   * @param head source branch (the branch containing changes)
   * @param base target branch (e.g. "main")
   * @param body PR description in markdown
   * @param draft whether to create as a draft PR
   * @param token GitHub token; if blank, falls back to defaultToken
   * @return raw JSON string of the created PR
   */
  public String createPullRequest(
      String owner,
      String repo,
      String title,
      String head,
      String base,
      String body,
      boolean draft,
      String token) {
    String bearerToken = resolveToken(token);
    try {
      ObjectNode requestNode = mapper.createObjectNode();
      requestNode.put("title", title);
      requestNode.put("head", head);
      requestNode.put("base", base);
      requestNode.put("body", body);
      requestNode.put("draft", draft);
      String requestBody = mapper.writeValueAsString(requestNode);

      log.debug("GitHub API: POST /repos/{}/{}/pulls head={} base={}", owner, repo, head, base);
      return restClient
          .post()
          .uri("/repos/{owner}/{repo}/pulls", owner, repo)
          .header("Authorization", "Bearer " + bearerToken)
          .header("Content-Type", "application/json")
          .body(requestBody)
          .retrieve()
          .body(String.class);
    } catch (Exception e) {
      String msg =
          String.format(
              "Failed to create PR for %s/%s (%s→%s): %s", owner, repo, head, base, e.getMessage());
      log.error(msg, e);
      throw new RuntimeException(msg, e);
    }
  }

  private String resolveToken(String token) {
    return (token != null && !token.isBlank()) ? token : defaultToken;
  }

  /**
   * Minimal JSON string escape — wraps value in quotes and escapes special chars. Using this avoids
   * pulling in a full JSON serializer for a single string field.
   */
  private static String jsonEscape(String value) {
    return "\""
        + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        + "\"";
  }
}
