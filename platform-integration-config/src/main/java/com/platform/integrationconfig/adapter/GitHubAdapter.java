package com.platform.integrationconfig.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.integration.*;
import com.platform.common.model.AutomatedTestRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Tier-3 IntegrationAdapter for GitHub source repositories.
 *
 * <p>Fetches automated-test references from the repo tree, handles commit/PR/comment push commands,
 * and maps push-webhook payloads to changed test files. Credentials: {@code connectionParams.pat}
 * (+ {@code owner}, {@code repo} or {@code repository}, optional {@code base_url} for GHE).
 */
@Component
public class GitHubAdapter
    implements IntegrationAdapter<AutomatedTestRef, GitHubAdapter.GitHubCommand> {

  private static final Logger log = LoggerFactory.getLogger(GitHubAdapter.class);

  private final RestClient.Builder restClientBuilder;
  private final ObjectMapper mapper;

  public GitHubAdapter(RestClient.Builder restClientBuilder, ObjectMapper mapper) {
    this.restClientBuilder = restClientBuilder;
    this.mapper = mapper;
  }

  @Override
  public IntegrationType type() {
    return IntegrationType.GITHUB;
  }

  @Override
  public PagedRecords<AutomatedTestRef> fetchPage(
      SourceIntegrationConfig config, SyncCursor cursor) {
    try {
      String[] or = ownerRepo(config);
      RestClient client = client(config);
      JsonNode repo =
          client.get().uri("/repos/{o}/{r}", or[0], or[1]).retrieve().body(JsonNode.class);
      String branch = repo == null ? "main" : repo.path("default_branch").asText("main");

      JsonNode tree =
          client
              .get()
              .uri("/repos/{o}/{r}/git/trees/{b}?recursive=1", or[0], or[1], branch)
              .retrieve()
              .body(JsonNode.class);

      List<AutomatedTestRef> out = new ArrayList<>();
      if (tree != null) {
        for (JsonNode node : tree.path("tree")) {
          if (!"blob".equals(node.path("type").asText())) continue;
          String path = node.path("path").asText();
          if (isTestFile(path)) out.add(toRef(path));
        }
      }
      return new PagedRecords<>(out, cursor.advance(Instant.now(), null));
    } catch (Exception e) {
      log.warn("[GitHubAdapter] fetchPage failed: {}", e.getMessage());
      return PagedRecords.empty(cursor.advance(Instant.now(), null));
    }
  }

  @Override
  public String push(SourceIntegrationConfig config, GitHubCommand command) {
    String[] or = ownerRepo(config);
    RestClient client = client(config);
    return switch (command.op()) {
      case COMMIT_FILE -> {
        var body = mapper.createObjectNode();
        body.put("message", command.commitMessage());
        body.put("content", command.content()); // base64
        body.put("branch", command.branch());
        JsonNode resp =
            client
                .put()
                .uri("/repos/{o}/{r}/contents/{p}", or[0], or[1], command.path())
                .header("Content-Type", "application/json")
                .body(body.toString())
                .retrieve()
                .body(JsonNode.class);
        yield resp == null ? null : resp.path("commit").path("sha").asText(null);
      }
      case CREATE_PR -> {
        JsonNode resp =
            client
                .post()
                .uri("/repos/{o}/{r}/pulls", or[0], or[1])
                .header("Content-Type", "application/json")
                .body(
                    Map.of(
                        "title",
                        command.commitMessage(),
                        "head",
                        command.branch(),
                        "base",
                        "main",
                        "body",
                        command.reviewBody() == null ? "" : command.reviewBody(),
                        "draft",
                        true))
                .retrieve()
                .body(JsonNode.class);
        yield resp == null ? null : resp.path("html_url").asText(null);
      }
      case POST_PR_COMMENT -> {
        JsonNode resp =
            client
                .post()
                .uri("/repos/{o}/{r}/issues/{n}/comments", or[0], or[1], command.prNumber())
                .header("Content-Type", "application/json")
                .body(Map.of("body", command.reviewBody() == null ? "" : command.reviewBody()))
                .retrieve()
                .body(JsonNode.class);
        yield resp == null ? null : resp.path("id").asText(null);
      }
    };
  }

  @Override
  public IntegrationHealth healthCheck(SourceIntegrationConfig config) {
    try {
      String[] or = ownerRepo(config);
      client(config).get().uri("/repos/{o}/{r}", or[0], or[1]).retrieve().body(String.class);
      return IntegrationHealth.healthy();
    } catch (Exception e) {
      return IntegrationHealth.down("GitHub repo health check failed: " + e.getMessage(), 1);
    }
  }

  @Override
  public List<AutomatedTestRef> fromWebhook(WebhookEvent event, SourceIntegrationConfig config) {
    // Map a push event's added/modified test files to AutomatedTestRefs.
    JsonNode root = mapper.valueToTree(event.rawPayload());
    List<AutomatedTestRef> out = new ArrayList<>();
    for (JsonNode commit : root.path("commits")) {
      collectPaths(commit.path("added"), out);
      collectPaths(commit.path("modified"), out);
    }
    return out;
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private void collectPaths(JsonNode arr, List<AutomatedTestRef> out) {
    if (arr.isArray()) {
      for (JsonNode p : arr) {
        String path = p.asText();
        if (isTestFile(path)) out.add(toRef(path));
      }
    }
  }

  private boolean isTestFile(String path) {
    String p = path.toLowerCase();
    return p.endsWith("test.java")
        || p.endsWith("tests.java")
        || p.endsWith(".spec.ts")
        || p.endsWith(".test.ts")
        || p.endsWith(".spec.js")
        || p.endsWith(".test.js")
        || p.endsWith(".feature")
        || p.contains("/test/")
        || p.contains("/tests/");
  }

  private AutomatedTestRef toRef(String path) {
    String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
    String className =
        fileName.contains(".") ? fileName.substring(0, fileName.indexOf('.')) : fileName;
    return new AutomatedTestRef(null, className, null, path, framework(path), null, Instant.now());
  }

  private String framework(String path) {
    String p = path.toLowerCase();
    if (p.endsWith(".feature")) return "CUCUMBER";
    if (p.endsWith(".spec.ts")
        || p.endsWith(".spec.js")
        || p.endsWith(".test.ts")
        || p.endsWith(".test.js")) return "PLAYWRIGHT";
    return "JUNIT5";
  }

  private RestClient client(SourceIntegrationConfig config) {
    String base = config.param("base_url");
    if (base == null || base.isBlank()) base = "https://api.github.com";
    if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
    String pat = config.param("pat");
    return restClientBuilder
        .clone()
        .baseUrl(base)
        .defaultHeader("Authorization", "Bearer " + (pat == null ? "" : pat))
        .defaultHeader("Accept", "application/vnd.github+json")
        .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
        .build();
  }

  private String[] ownerRepo(SourceIntegrationConfig config) {
    String repository = config.param("repository");
    if (repository == null) repository = config.param("repoFullName");
    if (repository != null && repository.contains("/")) {
      return repository.split("/", 2);
    }
    return new String[] {config.param("owner"), config.param("repo")};
  }

  /** Command for GitHub operations. */
  public record GitHubCommand(
      Op op,
      String owner,
      String repo,
      String branch,
      String path, // file path for COMMIT_FILE
      String content, // base64 or raw for COMMIT_FILE
      String commitMessage,
      String prNumber, // for PR_COMMENT
      String reviewBody) {
    public enum Op {
      COMMIT_FILE,
      CREATE_PR,
      POST_PR_COMMENT
    }
  }
}
