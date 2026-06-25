package com.platform.integrationconfig.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.integration.*;
import com.platform.common.model.AutomatedTestRef;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Tier-3 IntegrationAdapter for Azure DevOps Repos (Git).
 *
 * <p>Fetches automated-test references from the repo item tree, and supports create-PR /
 * PR-thread-comment push commands. {@code COMMIT_FILE} via the Azure Pushes API is not implemented
 * (requires the latest commit objectId); use the GitHub adapter for agent commit flows, or extend
 * here. Credentials: {@code connectionParams.pat} (+ {@code organization}, {@code project}, {@code
 * repository}, optional {@code base_url}).
 */
@Component
public class AzureReposAdapter
    implements IntegrationAdapter<AutomatedTestRef, AzureReposAdapter.AzureRepoCommand> {

  private static final Logger log = LoggerFactory.getLogger(AzureReposAdapter.class);
  private static final String API = "api-version=7.0";

  private final RestClient.Builder restClientBuilder;
  private final ObjectMapper mapper;

  public AzureReposAdapter(RestClient.Builder restClientBuilder, ObjectMapper mapper) {
    this.restClientBuilder = restClientBuilder;
    this.mapper = mapper;
  }

  @Override
  public IntegrationType type() {
    return IntegrationType.AZURE_DEVOPS_REPOS;
  }

  @Override
  public PagedRecords<AutomatedTestRef> fetchPage(
      SourceIntegrationConfig config, SyncCursor cursor) {
    try {
      JsonNode items =
          client(config)
              .get()
              .uri(
                  "/{p}/_apis/git/repositories/{r}/items?recursionLevel=Full&" + API,
                  project(config),
                  repo(config))
              .retrieve()
              .body(JsonNode.class);

      List<AutomatedTestRef> out = new ArrayList<>();
      if (items != null) {
        for (JsonNode item : items.path("value")) {
          if (!"blob".equalsIgnoreCase(item.path("gitObjectType").asText())) continue;
          String path = item.path("path").asText();
          if (isTestFile(path)) out.add(toRef(path));
        }
      }
      return new PagedRecords<>(out, cursor.advance(Instant.now(), null));
    } catch (Exception e) {
      log.warn("[AzureReposAdapter] fetchPage failed: {}", e.getMessage());
      return PagedRecords.empty(cursor.advance(Instant.now(), null));
    }
  }

  @Override
  public String push(SourceIntegrationConfig config, AzureRepoCommand command) {
    RestClient client = client(config);
    return switch (command.op()) {
      case CREATE_PR -> {
        JsonNode resp =
            client
                .post()
                .uri(
                    "/{p}/_apis/git/repositories/{r}/pullrequests?" + API,
                    project(config),
                    repo(config))
                .header("Content-Type", "application/json")
                .body(
                    Map.of(
                        "sourceRefName",
                        "refs/heads/" + command.branch(),
                        "targetRefName",
                        "refs/heads/main",
                        "title",
                        command.title(),
                        "description",
                        command.body() == null ? "" : command.body()))
                .retrieve()
                .body(JsonNode.class);
        yield resp == null ? null : resp.path("pullRequestId").asText(null);
      }
      case POST_PR_COMMENT -> {
        JsonNode resp =
            client
                .post()
                .uri(
                    "/{p}/_apis/git/repositories/{r}/pullRequests/{id}/threads?" + API,
                    project(config),
                    repo(config),
                    command.prId())
                .header("Content-Type", "application/json")
                .body(
                    Map.of(
                        "status",
                        "active",
                        "comments",
                        List.of(Map.of("content", command.body() == null ? "" : command.body()))))
                .retrieve()
                .body(JsonNode.class);
        yield resp == null ? null : resp.path("id").asText(null);
      }
      case COMMIT_FILE -> {
        log.warn(
            "[AzureReposAdapter] COMMIT_FILE not implemented (Pushes API requires latest"
                + " objectId)");
        yield null;
      }
    };
  }

  @Override
  public IntegrationHealth healthCheck(SourceIntegrationConfig config) {
    try {
      client(config)
          .get()
          .uri("/{p}/_apis/git/repositories/{r}?" + API, project(config), repo(config))
          .retrieve()
          .body(String.class);
      return IntegrationHealth.healthy();
    } catch (Exception e) {
      return IntegrationHealth.down("Azure Repos health check failed: " + e.getMessage(), 1);
    }
  }

  @Override
  public List<AutomatedTestRef> fromWebhook(WebhookEvent event, SourceIntegrationConfig config) {
    // Azure git.push payloads do not include changed file paths; refs are discovered via fetchPage.
    return List.of();
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private boolean isTestFile(String path) {
    String p = path.toLowerCase();
    return p.endsWith("test.java")
        || p.endsWith("tests.java")
        || p.endsWith(".spec.ts")
        || p.endsWith(".test.ts")
        || p.endsWith(".feature")
        || p.contains("/test/")
        || p.contains("/tests/");
  }

  private AutomatedTestRef toRef(String path) {
    String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
    String className =
        fileName.contains(".") ? fileName.substring(0, fileName.indexOf('.')) : fileName;
    String fw =
        path.toLowerCase().endsWith(".feature")
            ? "CUCUMBER"
            : (path.toLowerCase().contains(".spec.") || path.toLowerCase().contains(".test."))
                ? "PLAYWRIGHT"
                : "JUNIT5";
    return new AutomatedTestRef(null, className, null, path, fw, null, Instant.now());
  }

  private RestClient client(SourceIntegrationConfig config) {
    String base = config.param("base_url");
    if (base == null || base.isBlank()) base = "https://dev.azure.com";
    if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
    String org = config.param("organization");
    if (org != null && !base.endsWith("/" + org)) base = base + "/" + org;
    String pat = config.param("pat");
    String auth =
        "Basic "
            + Base64.getEncoder()
                .encodeToString((":" + (pat == null ? "" : pat)).getBytes(StandardCharsets.UTF_8));
    return restClientBuilder
        .clone()
        .baseUrl(base)
        .defaultHeader("Authorization", auth)
        .defaultHeader("Accept", "application/json")
        .build();
  }

  private String project(SourceIntegrationConfig config) {
    String p = config.param("project");
    return p != null ? p : config.param("project_key");
  }

  private String repo(SourceIntegrationConfig config) {
    String r = config.param("repository");
    return r != null ? r : config.param("repo");
  }

  /** Command for Azure Repos operations. */
  public record AzureRepoCommand(Op op, String branch, String title, String body, String prId) {
    public enum Op {
      CREATE_PR,
      POST_PR_COMMENT,
      COMMIT_FILE
    }
  }
}
