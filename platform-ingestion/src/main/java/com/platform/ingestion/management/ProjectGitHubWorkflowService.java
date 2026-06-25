package com.platform.ingestion.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.IntegrationCredential;
import com.platform.core.domain.ProjectRepoAssignment;
import com.platform.core.repository.IntegrationCredentialRepository;
import com.platform.core.repository.ProjectRepoAssignmentRepository;
import com.platform.core.service.CredentialCipher;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reads and triggers GitHub Actions workflows for TEST_AUTOMATION repos assigned to a project. All
 * calls are live (no local cache) since workflow metadata and runs are small and change frequently.
 */
@Service
@Transactional(readOnly = true)
public class ProjectGitHubWorkflowService {

  private final ProjectRepoAssignmentRepository assignmentRepo;
  private final IntegrationCredentialRepository credRepo;
  private final CredentialCipher cipher;
  private final ObjectMapper om;
  private final HttpClient http = trustAllClient(Duration.ofSeconds(10));

  public ProjectGitHubWorkflowService(
      ProjectRepoAssignmentRepository assignmentRepo,
      IntegrationCredentialRepository credRepo,
      CredentialCipher cipher,
      ObjectMapper om) {
    this.assignmentRepo = assignmentRepo;
    this.credRepo = credRepo;
    this.cipher = cipher;
    this.om = om;
  }

  // ── DTOs ──────────────────────────────────────────────────────────────────

  public record WorkflowDto(
      long id, String name, String path, String state, String htmlUrl, String repoFullName) {}

  public record RunDto(
      long id,
      String displayTitle,
      String status,
      String conclusion,
      String branch,
      String event,
      String htmlUrl,
      Instant createdAt,
      Instant updatedAt,
      String headSha,
      String repoFullName,
      long workflowId) {}

  public record DispatchResult(boolean triggered, String message) {}

  // ── Public API ────────────────────────────────────────────────────────────

  /** All GitHub Actions workflows across TEST_AUTOMATION repos for this project. */
  public List<WorkflowDto> listWorkflows(UUID projectId) {
    List<WorkflowDto> result = new ArrayList<>();
    for (ProjectRepoAssignment a : testAutoAssignments(projectId)) {
      try {
        ApiCtx ctx = ctx(a.getCredentialId());
        String[] parts = split(a.getRepoFullName());
        String url = ctx.baseUrl + "/repos/" + parts[0] + "/" + parts[1] + "/actions/workflows";
        JsonNode root = githubGet(url, ctx.pat);
        if (root == null) continue;
        for (JsonNode wf : root.path("workflows")) {
          result.add(
              new WorkflowDto(
                  wf.path("id").asLong(),
                  wf.path("name").asText(""),
                  wf.path("path").asText(""),
                  wf.path("state").asText(""),
                  wf.path("html_url").asText(""),
                  a.getRepoFullName()));
        }
      } catch (ResponseStatusException e) {
        throw e;
      } catch (Exception ignored) {
        // individual repo failure doesn't block the rest
      }
    }
    return result;
  }

  /** Recent runs for a specific workflow. */
  public List<RunDto> listRuns(UUID projectId, String repoFullName, long workflowId, int limit) {
    ApiCtx ctx = ctxForRepo(projectId, repoFullName);
    String[] parts = split(repoFullName);
    String url =
        ctx.baseUrl
            + "/repos/"
            + parts[0]
            + "/"
            + parts[1]
            + "/actions/workflows/"
            + workflowId
            + "/runs?per_page="
            + Math.min(limit, 50);
    JsonNode root = githubGet(url, ctx.pat);
    if (root == null) return List.of();

    List<RunDto> out = new ArrayList<>();
    for (JsonNode r : root.path("workflow_runs")) {
      out.add(
          new RunDto(
              r.path("id").asLong(),
              r.path("display_title").asText(r.path("name").asText("")),
              r.path("status").asText(""),
              r.path("conclusion").asText(null),
              r.path("head_branch").asText(""),
              r.path("event").asText(""),
              r.path("html_url").asText(""),
              parseInstant(r.path("created_at").asText(null)),
              parseInstant(r.path("updated_at").asText(null)),
              r.path("head_sha").asText(""),
              repoFullName,
              workflowId));
    }
    return out;
  }

  public record DispatchRequest(
      String repoFullName, long workflowId, String ref, Map<String, String> inputs) {}

  /** Trigger a workflow_dispatch event. Requires the PAT to have `workflow` scope. */
  @Transactional
  public DispatchResult triggerDispatch(UUID projectId, DispatchRequest req) {
    ApiCtx ctx = ctxForRepo(projectId, req.repoFullName());
    String[] parts = split(req.repoFullName());
    String url =
        ctx.baseUrl
            + "/repos/"
            + parts[0]
            + "/"
            + parts[1]
            + "/actions/workflows/"
            + req.workflowId()
            + "/dispatches";

    Map<String, Object> body =
        req.inputs() != null && !req.inputs().isEmpty()
            ? Map.of("ref", req.ref(), "inputs", req.inputs())
            : Map.of("ref", req.ref());

    try {
      String json = om.writeValueAsString(body);
      HttpRequest httpReq =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(15))
              .header("Authorization", "Bearer " + ctx.pat)
              .header("Accept", "application/vnd.github+json")
              .header("X-GitHub-Api-Version", "2022-11-28")
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json))
              .build();
      HttpResponse<String> resp = http.send(httpReq, HttpResponse.BodyHandlers.ofString());
      int code = resp.statusCode();
      if (code == 204) return new DispatchResult(true, "Workflow dispatch triggered successfully");
      if (code == 422)
        return new DispatchResult(
            false,
            "GitHub rejected dispatch (422) — check that workflow_dispatch trigger is configured"
                + " and the ref exists");
      if (code == 403)
        return new DispatchResult(
            false, "PAT lacks 'workflow' scope — re-generate with workflow permission");
      return new DispatchResult(false, "GitHub returned HTTP " + code);
    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Dispatch request failed: " + e.getMessage());
    }
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private List<ProjectRepoAssignment> testAutoAssignments(UUID projectId) {
    return assignmentRepo.findByProjectIdOrderByRepoFullName(projectId).stream()
        .filter(a -> "TEST_AUTOMATION".equals(a.getRole()))
        .toList();
  }

  private record ApiCtx(String pat, String baseUrl) {}

  private ApiCtx ctxForRepo(UUID projectId, String repoFullName) {
    ProjectRepoAssignment a =
        assignmentRepo.findByProjectIdOrderByRepoFullName(projectId).stream()
            .filter(x -> repoFullName.equals(x.getRepoFullName()))
            .findFirst()
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Repo not assigned to project: " + repoFullName));
    return ctx(a.getCredentialId());
  }

  private ApiCtx ctx(UUID credentialId) {
    IntegrationCredential cred =
        credRepo
            .findById(credentialId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Credential not found: " + credentialId));
    return new ApiCtx(decryptPat(cred), baseUrl(cred));
  }

  @SuppressWarnings("unchecked")
  private String decryptPat(IntegrationCredential c) {
    if (c.getSecretCiphertext() == null || c.getSecretCiphertext().isBlank())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credential has no PAT");
    try {
      Map<String, String> secret = om.readValue(cipher.decrypt(c.getSecretCiphertext()), Map.class);
      String pat = secret.getOrDefault("pat", secret.get("token"));
      if (pat == null || pat.isBlank())
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credential PAT is empty");
      return pat;
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read credential secret");
    }
  }

  private static String baseUrl(IntegrationCredential c) {
    String b = c.getBaseUrl();
    if (b == null || b.isBlank()) return "https://api.github.com";
    return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
  }

  private static String[] split(String fullName) {
    String[] p = fullName.split("/", 2);
    if (p.length < 2)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Invalid repo name (expected owner/repo): " + fullName);
    return p;
  }

  private static Instant parseInstant(String s) {
    if (s == null || s.isBlank()) return null;
    try {
      return Instant.parse(s);
    } catch (Exception e) {
      return null;
    }
  }

  private JsonNode githubGet(String url, String pat) {
    try {
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(20))
              .header("Authorization", "Bearer " + pat)
              .header("Accept", "application/vnd.github+json")
              .header("X-GitHub-Api-Version", "2022-11-28")
              .GET()
              .build();
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      int code = resp.statusCode();
      if (code == 401 || code == 403)
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "GitHub auth failed (HTTP " + code + ") — check PAT scopes");
      if (code < 200 || code >= 300)
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub returned HTTP " + code);
      return om.readTree(resp.body());
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "GitHub request failed: " + e.getMessage());
    }
  }

  private static HttpClient trustAllClient(Duration connectTimeout) {
    try {
      SSLContext ctx = SSLContext.getInstance("TLS");
      ctx.init(
          null,
          new TrustManager[] {
            new X509TrustManager() {
              public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
              }

              public void checkClientTrusted(X509Certificate[] c, String a) {}

              public void checkServerTrusted(X509Certificate[] c, String a) {}
            }
          },
          new SecureRandom());
      return HttpClient.newBuilder().connectTimeout(connectTimeout).sslContext(ctx).build();
    } catch (Exception e) {
      throw new IllegalStateException("Cannot create HTTP client", e);
    }
  }
}
