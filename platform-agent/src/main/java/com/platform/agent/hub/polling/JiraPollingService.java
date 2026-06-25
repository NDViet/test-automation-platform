package com.platform.agent.hub.polling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.ProjectIntegrationConfig;
import com.platform.core.repository.PlatformRequirementRepository;
import com.platform.core.repository.ProjectIntegrationConfigRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * Polls Jira Cloud or Server for issues and upserts them as platform requirements.
 *
 * <p>Auth: Basic base64(email:apiToken) — same scheme as JiraClient in platform-integration. Jira
 * Cloud description fields are ADF (Atlassian Document Format); plain text is extracted by walking
 * the content[] tree. Jira Server sends plain strings — both are handled.
 *
 * <p>Config keys in ProjectIntegrationConfig.connectionParams: baseUrl —
 * https://yourorg.atlassian.net (Cloud) or https://jira.company.com (Server) email — Atlassian
 * account email (Cloud); use "username" key for Server username — Basic auth username for Server
 * (used when "email" is absent) apiToken — API token (Cloud) or password (Server) projectKey — Jira
 * project key, e.g. PROJ
 *
 * <p>filterConfig keys (optional): jql — JQL override; defaults to: project = {projectKey} ORDER BY
 * created DESC
 */
@Service
public class JiraPollingService {

  private static final Logger log = LoggerFactory.getLogger(JiraPollingService.class);
  private static final int MAX_RESULTS = 50;
  private static final int MAX_ISSUES = 500;

  private final ProjectIntegrationConfigRepository configRepo;
  private final PlatformRequirementRepository requirementRepo;
  private final ObjectMapper mapper;

  public JiraPollingService(
      ProjectIntegrationConfigRepository configRepo,
      PlatformRequirementRepository requirementRepo,
      ObjectMapper mapper) {
    this.configRepo = configRepo;
    this.requirementRepo = requirementRepo;
    this.mapper = mapper;
  }

  /**
   * Fetches Jira issues for the project and upserts them as platform requirements. Returns the
   * total number of issues synced (created or updated).
   */
  @Transactional
  public int syncNow(UUID projectId, String integrationType) {
    ProjectIntegrationConfig config =
        configRepo
            .findByProjectIdAndIntegrationType(projectId, integrationType)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "No " + integrationType + " integration config for project " + projectId));

    String baseUrl = config.param("baseUrl");
    String user = config.param("email") != null ? config.param("email") : config.param("username");
    String apiToken = config.param("apiToken");
    String projectKey = config.param("projectKey");

    if (baseUrl == null || baseUrl.isBlank())
      throw new IllegalArgumentException("Jira config missing 'baseUrl' for project " + projectId);
    if (user == null || user.isBlank())
      throw new IllegalArgumentException(
          "Jira config missing 'email'/'username' for project " + projectId);
    if (apiToken == null || apiToken.isBlank())
      throw new IllegalArgumentException("Jira config missing 'apiToken' for project " + projectId);
    if (projectKey == null || projectKey.isBlank())
      throw new IllegalArgumentException(
          "Jira config missing 'projectKey' for project " + projectId);

    String jql =
        config.getFilterConfig() != null
            ? config.getFilterConfig().getOrDefault("jql", null)
            : null;
    if (jql == null || jql.isBlank()) {
      jql = "project = " + projectKey + " ORDER BY created DESC";
    }

    String authHeader =
        "Basic "
            + Base64.getEncoder()
                .encodeToString((user + ":" + apiToken).getBytes(StandardCharsets.UTF_8));
    String normalizedBase =
        baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

    RestClient client =
        RestClient.builder()
            .baseUrl(normalizedBase)
            .defaultHeader("Authorization", authHeader)
            .defaultHeader("Accept", "application/json")
            .build();

    int total = 0;
    String nextPageToken = null;
    // childExternalId → parentExternalId collected during sync; resolved after
    Map<String, String> parentKeyMap = new HashMap<>();

    try {
      // POST /rest/api/3/search/jql uses cursor-based pagination (nextPageToken),
      // not offset-based (startAt was removed along with the old GET endpoint).
      // https://developer.atlassian.com/changelog/#CHANGE-2046
      while (total < MAX_ISSUES) {
        StringBuilder body =
            new StringBuilder()
                .append("{\"jql\":\"")
                .append(jql.replace("\"", "\\\""))
                .append("\"")
                .append(",\"maxResults\":")
                .append(MAX_RESULTS)
                .append(
                    ",\"fields\":[\"key\",\"summary\",\"description\",\"issuetype\",\"status\",\"priority\",\"parent\"]");
        if (nextPageToken != null) {
          body.append(",\"nextPageToken\":\"").append(nextPageToken).append("\"");
        }
        body.append("}");

        String raw =
            client
                .post()
                .uri("/rest/api/3/search/jql")
                .header("Content-Type", "application/json")
                .body(body.toString())
                .retrieve()
                .body(String.class);
        if (raw == null || raw.isBlank()) break;
        JsonNode page = mapper.readTree(raw);

        JsonNode issues = page.path("issues");
        int fetched = 0;

        for (JsonNode issue : issues) {
          String issueKey = issue.path("key").asText("");
          if (issueKey.isBlank()) continue;
          JsonNode fields = issue.path("fields");
          String summary = fields.path("summary").asText("(no title)");
          String description = extractDescription(fields.path("description"));
          String issueType = fields.path("issuetype").path("name").asText("STORY");

          // Collect parent key for hierarchy resolution after bulk upsert
          String parentKey = fields.path("parent").path("key").asText(null);
          if (parentKey != null && !parentKey.isBlank()) {
            parentKeyMap.put(issueKey, parentKey);
          }

          requirementRepo.upsert(
              config.getProjectId(),
              config.getId(),
              issueKey,
              summary,
              description,
              normaliseIssueType(issueType),
              Instant.now());
          total++;
          fetched++;
        }

        JsonNode tokenNode = page.path("nextPageToken");
        if (fetched == 0 || tokenNode.isMissingNode() || tokenNode.isNull()) break;
        nextPageToken = tokenNode.asText();
      }

      // Second pass: resolve parent external IDs to platform UUIDs and set parentId + depth.
      // Must run after all upserts so that parent rows exist in the DB.
      resolveHierarchy(config.getProjectId(), parentKeyMap);

      config.recordSyncSuccess();
      configRepo.save(config);
      log.info(
          "Jira sync: {} issue(s) synced for project {} ({} parent links resolved)",
          total,
          projectId,
          parentKeyMap.size());

    } catch (Exception e) {
      config.recordSyncError();
      configRepo.save(config);
      throw e instanceof RuntimeException re ? re : new RuntimeException(e.getMessage(), e);
    }

    return total;
  }

  // ── Hierarchy resolution ──────────────────────────────────────────────────

  /**
   * For each child→parent external-ID pair collected during sync, looks up both rows in the DB and
   * calls updateParent() to wire the parentId + depth. Depth is computed from the parent's depth +
   * 1; if the parent's depth is still 0 (not yet resolved in this pass), it defaults to
   * issueType-based depth.
   */
  private void resolveHierarchy(UUID projectId, Map<String, String> parentKeyMap) {
    if (parentKeyMap.isEmpty()) return;
    Instant now = Instant.now();
    int linked = 0;
    for (Map.Entry<String, String> entry : parentKeyMap.entrySet()) {
      String childKey = entry.getKey();
      String parentKey = entry.getValue();
      try {
        var childOpt = requirementRepo.findByProjectIdAndExternalId(projectId, childKey);
        var parentOpt = requirementRepo.findByProjectIdAndExternalId(projectId, parentKey);
        if (childOpt.isEmpty() || parentOpt.isEmpty()) continue;

        var child = childOpt.get();
        var parent = parentOpt.get();
        int depth = parent.getDepth() + 1;
        requirementRepo.updateParent(child.getId(), parent.getId(), depth, now);
        linked++;
      } catch (Exception e) {
        log.warn("Failed to link {} → {}: {}", childKey, parentKey, e.getMessage());
      }
    }
    if (linked > 0) {
      log.debug("Hierarchy: linked {} child→parent pairs for project {}", linked, projectId);
    }
  }

  // ── Jira description extraction ───────────────────────────────────────────

  private String extractDescription(JsonNode descNode) {
    if (descNode == null || descNode.isNull() || descNode.isMissingNode()) return "";
    if (descNode.isTextual()) return descNode.asText();
    // Jira Cloud sends ADF; walk content[] → paragraph → text nodes
    StringBuilder sb = new StringBuilder();
    extractAdfText(descNode, sb);
    return sb.toString().trim();
  }

  private void extractAdfText(JsonNode node, StringBuilder sb) {
    if (node.isTextual()) {
      sb.append(node.asText());
      return;
    }
    JsonNode textNode = node.path("text");
    if (textNode.isTextual()) sb.append(textNode.asText());
    JsonNode content = node.path("content");
    if (content.isArray()) {
      content.forEach(
          child -> {
            extractAdfText(child, sb);
            if ("paragraph".equals(child.path("type").asText())) sb.append("\n");
          });
    }
  }

  private String normaliseIssueType(String raw) {
    return switch (raw.toUpperCase()) {
      case "EPIC" -> "EPIC";
      case "BUG", "DEFECT" -> "DEFECT";
      case "SUBTASK", "SUB-TASK" -> "SUBTASK";
      case "TASK" -> "TASK";
      case "SPIKE" -> "SPIKE";
      default -> "STORY";
    };
  }
}
