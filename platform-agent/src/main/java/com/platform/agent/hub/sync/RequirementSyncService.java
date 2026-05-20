package com.platform.agent.hub.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.integration.IntegrationType;
import com.platform.core.domain.ProjectIntegrationConfig;
import com.platform.core.repository.PlatformRequirementRepository;
import com.platform.core.repository.ProjectIntegrationConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Upserts requirements into platform_requirements from JIRA and Linear webhook payloads.
 * Each upsert is idempotent — if title+description haven't changed, the row is untouched.
 *
 * This is called synchronously before the workflow is triggered so ContextAssembler
 * can include the freshly-synced requirement in the ContextBundle.
 */
@Service
public class RequirementSyncService {

    private static final Logger log = LoggerFactory.getLogger(RequirementSyncService.class);

    private final PlatformRequirementRepository requirementRepo;
    private final ProjectIntegrationConfigRepository configRepo;
    private final ObjectMapper mapper;

    public RequirementSyncService(PlatformRequirementRepository requirementRepo,
                                   ProjectIntegrationConfigRepository configRepo,
                                   ObjectMapper mapper) {
        this.requirementRepo = requirementRepo;
        this.configRepo      = configRepo;
        this.mapper          = mapper;
    }

    /**
     * Upsert from a JIRA webhook payload.
     * Returns the projectId of the matched project, or empty if no matching config found.
     */
    @Transactional
    public Optional<UUID> syncFromJira(String payloadJson) {
        try {
            JsonNode root      = mapper.readTree(payloadJson);
            JsonNode issue     = root.path("issue");
            JsonNode fields    = issue.path("fields");

            String issueKey    = issue.path("key").asText();                  // e.g. PROJ-123
            String projectKey  = issueKey.contains("-") ? issueKey.split("-")[0] : issueKey;
            String summary     = fields.path("summary").asText("(no title)");
            String description = extractJiraDescription(fields.path("description"));
            String issueType   = fields.path("issuetype").path("name").asText("STORY").toUpperCase();

            Optional<ProjectIntegrationConfig> configOpt = findJiraConfig(projectKey);
            if (configOpt.isEmpty()) {
                log.debug("JIRA sync: no config for project key '{}', skipping", projectKey);
                return Optional.empty();
            }

            ProjectIntegrationConfig config = configOpt.get();
            int rows = requirementRepo.upsert(
                    config.getProjectId(), config.getId(),
                    issueKey, summary, description, normaliseIssueType(issueType),
                    Instant.now());

            log.info("JIRA sync: upserted requirement {} for project {} (rows affected={})",
                    issueKey, config.getProjectId(), rows);
            return Optional.of(config.getProjectId());

        } catch (Exception e) {
            log.error("JIRA sync failed: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Upsert from a Linear webhook payload.
     * Returns the projectId of the matched project, or empty if no matching config found.
     */
    @Transactional
    public Optional<UUID> syncFromLinear(String payloadJson) {
        try {
            JsonNode root       = mapper.readTree(payloadJson);
            JsonNode data       = root.path("data");

            String identifier   = data.path("identifier").asText();           // e.g. ENG-42
            String teamId       = data.path("team").path("id").asText();
            String title        = data.path("title").asText("(no title)");
            String description  = data.path("description").asText("");
            String type         = data.path("type").asText("STORY").toUpperCase();

            Optional<ProjectIntegrationConfig> configOpt = findLinearConfig(teamId);
            if (configOpt.isEmpty()) {
                log.debug("Linear sync: no config for teamId '{}', skipping", teamId);
                return Optional.empty();
            }

            ProjectIntegrationConfig config = configOpt.get();
            int rows = requirementRepo.upsert(
                    config.getProjectId(), config.getId(),
                    identifier, title, description, normaliseIssueType(type),
                    Instant.now());

            log.info("Linear sync: upserted requirement {} for project {} (rows affected={})",
                    identifier, config.getProjectId(), rows);
            return Optional.of(config.getProjectId());

        } catch (Exception e) {
            log.error("Linear sync failed: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    // -------------------------------------------------------------------------

    private Optional<ProjectIntegrationConfig> findJiraConfig(String projectKey) {
        List<ProjectIntegrationConfig> all = configRepo.findByIntegrationTypeAndEnabled(
                IntegrationType.JIRA_CLOUD.name(), true);
        // Also check JIRA_SERVER
        all.addAll(configRepo.findByIntegrationTypeAndEnabled(IntegrationType.JIRA_SERVER.name(), true));
        return all.stream()
                .filter(c -> projectKey.equalsIgnoreCase(c.param("projectKey")))
                .findFirst();
    }

    private Optional<ProjectIntegrationConfig> findLinearConfig(String teamId) {
        return configRepo.findByIntegrationTypeAndEnabled(IntegrationType.LINEAR.name(), true)
                .stream()
                .filter(c -> teamId.equals(c.param("teamId")))
                .findFirst();
    }

    /**
     * Extracts plain text from a JIRA description field.
     * JIRA Cloud sends Atlassian Document Format (ADF) as a nested JSON object;
     * JIRA Server sends plain text. Handles both.
     */
    private String extractJiraDescription(JsonNode descNode) {
        if (descNode.isNull() || descNode.isMissingNode()) return "";
        if (descNode.isTextual()) return descNode.asText();
        // ADF: walk content[] → paragraph → text nodes
        StringBuilder sb = new StringBuilder();
        extractAdfText(descNode, sb);
        return sb.toString().trim();
    }

    private void extractAdfText(JsonNode node, StringBuilder sb) {
        if (node.isTextual()) { sb.append(node.asText()); return; }
        JsonNode textNode = node.path("text");
        if (textNode.isTextual()) sb.append(textNode.asText());
        JsonNode content = node.path("content");
        if (content.isArray()) {
            content.forEach(child -> {
                extractAdfText(child, sb);
                if ("paragraph".equals(child.path("type").asText())) sb.append("\n");
            });
        }
    }

    private String normaliseIssueType(String raw) {
        return switch (raw.toUpperCase()) {
            case "EPIC"                  -> "EPIC";
            case "BUG", "DEFECT"        -> "DEFECT";
            case "SUBTASK", "SUB-TASK"  -> "SUBTASK";
            case "TASK"                  -> "TASK";
            case "SPIKE"                 -> "SPIKE";
            default                      -> "STORY";
        };
    }
}
