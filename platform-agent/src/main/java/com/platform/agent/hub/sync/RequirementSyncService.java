package com.platform.agent.hub.sync;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.integration.IntegrationType;
import com.platform.core.domain.PlatformRequirement;
import com.platform.core.domain.ProjectIntegrationConfig;
import com.platform.core.mapping.MappingProfileApplier;
import com.platform.core.mapping.MappingRules;
import com.platform.core.mapping.MappingRulesProvider;
import com.platform.core.repository.PlatformRequirementRepository;
import com.platform.core.repository.ProjectIntegrationConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final MappingRulesProvider rulesProvider;
    private final MappingProfileApplier applier;
    private final ObjectMapper mapper;

    public RequirementSyncService(PlatformRequirementRepository requirementRepo,
                                   ProjectIntegrationConfigRepository configRepo,
                                   MappingRulesProvider rulesProvider,
                                   MappingProfileApplier applier,
                                   ObjectMapper mapper) {
        this.requirementRepo = requirementRepo;
        this.configRepo      = configRepo;
        this.rulesProvider   = rulesProvider;
        this.applier         = applier;
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
            Map<String, Object> raw = toMap(fields);
            String state = fields.path("status").path("name").asText(null);
            int rows = mergeRequirement(config, issueKey, summary, description,
                    issueType, raw, state);

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
            Map<String, Object> raw = toMap(data);
            String state = data.path("state").path("name").asText(null);
            int rows = mergeRequirement(config, identifier, title, description,
                    type, raw, state);

            log.info("Linear sync: upserted requirement {} for project {} (rows affected={})",
                    identifier, config.getProjectId(), rows);
            return Optional.of(config.getProjectId());

        } catch (Exception e) {
            log.error("Linear sync failed: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Upsert from an Azure DevOps Boards Service Hook payload
     * ({@code workitem.created} / {@code workitem.updated}).
     * Returns the projectId of the matched project, or empty if no config matches.
     */
    @Transactional
    public Optional<UUID> syncFromAzureBoards(String payloadJson) {
        try {
            JsonNode root     = mapper.readTree(payloadJson);
            JsonNode resource = root.path("resource");
            // On updates, the full snapshot is under resource.revision.fields;
            // on creates the values are directly under resource.fields.
            JsonNode fields   = resource.path("revision").path("fields");
            if (fields.isMissingNode() || fields.isEmpty()) fields = resource.path("fields");

            String workItemId = resource.path("id").asText();
            if (workItemId.isBlank()) workItemId = resource.path("workItemId").asText();
            String title       = azureField(fields, "System.Title", "(no title)");
            String description = azureField(fields, "System.Description", "");
            String teamProject = azureField(fields, "System.TeamProject", "");
            String type        = azureField(fields, "System.WorkItemType", "STORY");

            if (workItemId.isBlank()) {
                log.warn("Azure Boards sync: payload has no work item id, skipping");
                return Optional.empty();
            }

            Optional<ProjectIntegrationConfig> configOpt = findAzureBoardsConfig(teamProject);
            if (configOpt.isEmpty()) {
                log.debug("Azure Boards sync: no config for project '{}', skipping", teamProject);
                return Optional.empty();
            }

            ProjectIntegrationConfig config = configOpt.get();
            Map<String, Object> raw = azureFieldsToMap(fields);
            String state = azureField(fields, "System.State", null);
            int rows = mergeRequirement(config, workItemId, title, description,
                    type, raw, state);

            log.info("Azure Boards sync: upserted requirement {} for project {} (rows affected={})",
                    workItemId, config.getProjectId(), rows);
            return Optional.of(config.getProjectId());

        } catch (Exception e) {
            log.error("Azure Boards sync failed: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Upsert from a GitHub Issues webhook payload ({@code issues} event).
     * Returns the projectId of the matched project, or empty if no config matches.
     */
    @Transactional
    public Optional<UUID> syncFromGitHubIssues(String payloadJson) {
        try {
            JsonNode root  = mapper.readTree(payloadJson);
            JsonNode issue = root.path("issue");
            String repoFullName = root.path("repository").path("full_name").asText();

            String number      = issue.path("number").asText();
            String title       = issue.path("title").asText("(no title)");
            String description = issue.path("body").asText("");
            String type        = gitHubIssueType(issue);
            String externalId  = repoFullName + "#" + number;

            if (number.isBlank() || repoFullName.isBlank()) {
                log.warn("GitHub Issues sync: payload missing issue number or repository, skipping");
                return Optional.empty();
            }

            Optional<ProjectIntegrationConfig> configOpt = findGitHubIssuesConfig(repoFullName);
            if (configOpt.isEmpty()) {
                log.debug("GitHub Issues sync: no config for repo '{}', skipping", repoFullName);
                return Optional.empty();
            }

            ProjectIntegrationConfig config = configOpt.get();
            Map<String, Object> raw = toMap(issue);
            String state = issue.path("state").asText(null);   // open / closed
            int rows = mergeRequirement(config, externalId, title, description,
                    type, raw, state);

            log.info("GitHub Issues sync: upserted requirement {} for project {} (rows affected={})",
                    externalId, config.getProjectId(), rows);
            return Optional.of(config.getProjectId());

        } catch (Exception e) {
            log.error("GitHub Issues sync failed: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    // -------------------------------------------------------------------------

    /** Result of an upsert: the persisted requirement and whether any canonical field changed. */
    public record Upserted(PlatformRequirement requirement, boolean changed) {}

    /**
     * Merge-safe, profile-driven upsert (shared by webhook and poll paths). Applies the
     * project's resolved mapping profile to derive canonical status/priority, stores the
     * full upstream payload, and updates existing rows non-destructively (a field that
     * disappeared upstream keeps its last-known value instead of being clobbered).
     */
    @Transactional
    public Upserted upsertRequirement(ProjectIntegrationConfig config, String externalId,
                                      String title, String description, String issueType,
                                      Map<String, Object> rawFields, String stateName) {
        UUID projectId = config.getProjectId();
        MappingRules rules = rulesProvider.effectiveForProject(projectId);
        // Issue type is profile-driven (e.g. "Acme Bug" → DEFECT, "Acme Team Epic" → EPIC);
        // fall back to stock normalization for non-tracked / unrecognised types.
        String profiled = applier.issueType(rules, issueType);
        String type = profiled != null ? profiled : normaliseIssueType(issueType);
        String status   = applier.status(rules, stateName).value();            // null if unresolved
        String priority = applier.priority(rules, stringMap(rawFields)).value(); // null if unresolved

        List<Object> acceptanceCriteria = extractAcceptanceCriteria(rawFields);
        String areaPath      = strField(rawFields, "System.AreaPath");
        String iterationPath = strField(rawFields, "System.IterationPath");
        String assignedTo    = strField(rawFields, "System.AssignedTo");
        Instant createdDate  = parseInstant(strField(rawFields, "System.CreatedDate"));

        Optional<PlatformRequirement> existing =
                requirementRepo.findByProjectIdAndExternalId(projectId, externalId);

        if (existing.isEmpty()) {
            PlatformRequirement r = new PlatformRequirement(
                    projectId, config.getId(), externalId, title, description, type);
            if (status != null)   r.setStatus(status);
            if (priority != null) r.setPriority(priority);
            if (!acceptanceCriteria.isEmpty()) r.setAcceptanceCriteria(acceptanceCriteria);
            r.setDimensions(areaPath, iterationPath, assignedTo);
            r.setCreatedDate(createdDate);
            r.setRawUpstream(rawFields);
            return new Upserted(requirementRepo.save(r), true);
        }
        PlatformRequirement r = existing.get();
        boolean changed = r.mergeFromSync(title, description, type, status, priority, rawFields);
        r.setDimensions(areaPath, iterationPath, assignedTo);
        r.setCreatedDate(createdDate);
        // Non-destructive: only overwrite ACs when upstream actually provides them.
        if (!acceptanceCriteria.isEmpty() && !acceptanceCriteria.equals(r.getAcceptanceCriteria())) {
            r.setAcceptanceCriteria(acceptanceCriteria);
            changed = true;
        }
        return new Upserted(requirementRepo.save(r), changed);
    }

    /** Webhook helper: upsert and report 1 if created/changed, else 0. */
    private int mergeRequirement(ProjectIntegrationConfig config, String externalId,
                                 String title, String description, String issueType,
                                 Map<String, Object> rawFields, String stateName) {
        return upsertRequirement(config, externalId, title, description, issueType, rawFields, stateName)
                .changed() ? 1 : 0;
    }

    /** Parses an ISO-8601 timestamp (ADO dates), or null. */
    private static Instant parseInstant(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Instant.parse(s); } catch (Exception e) {
            try { return java.time.OffsetDateTime.parse(s).toInstant(); } catch (Exception ex) { return null; }
        }
    }

    /** Reads a non-blank string field from the flattened raw payload, or null. */
    private static String strField(Map<String, Object> rawFields, String key) {
        if (rawFields == null) return null;
        Object v = rawFields.get(key);
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static final String AC_FIELD = "Microsoft.VSTS.Common.AcceptanceCriteria";
    private static final java.util.regex.Pattern LI_ITEM =
            java.util.regex.Pattern.compile("(?is)<li[^>]*>(.*?)</li>");

    /**
     * Extracts structured acceptance criteria from the upstream payload. Azure Boards stores
     * AC as HTML (typically a {@code <ul><li>…</li></ul>} list) under
     * {@code Microsoft.VSTS.Common.AcceptanceCriteria}; we split list items into separate
     * criteria, falling back to line-splitting when there are no {@code <li>} items.
     */
    private List<Object> extractAcceptanceCriteria(Map<String, Object> rawFields) {
        if (rawFields == null) return List.of();
        Object raw = rawFields.get(AC_FIELD);
        if (raw == null) return List.of();
        String html = String.valueOf(raw);
        if (html.isBlank()) return List.of();

        List<Object> out = new java.util.ArrayList<>();
        java.util.regex.Matcher m = LI_ITEM.matcher(html);
        while (m.find()) {
            String item = stripHtml(m.group(1));
            if (!item.isBlank()) out.add(item);
        }
        if (out.isEmpty()) {
            String flattened = stripHtml(html.replaceAll("(?i)<br\\s*/?>", "\n"));
            for (String line : flattened.split("\\r?\\n")) {
                String t = line.trim();
                if (!t.isBlank()) out.add(t);
            }
        }
        return out;
    }

    /** Strips HTML tags and decodes the common entities to readable plain text. */
    private String stripHtml(String s) {
        return s.replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                .replaceAll("\\s+", " ").trim();
    }

    /** Converts a JSON node to a Map for provenance storage (never throws). */
    private Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return Map.of();
        try {
            return mapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** Flattens an Azure {@code fields} node (handling {oldValue,newValue}) to refName→value. */
    private Map<String, Object> azureFieldsToMap(JsonNode fields) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (fields == null || !fields.isObject()) return out;
        fields.fieldNames().forEachRemaining(fn -> {
            String v = azureField(fields, fn, null);
            if (v != null) out.put(fn, v);
        });
        return out;
    }

    /** String view of a raw field map for the priority value-rule lookup. */
    private Map<String, String> stringMap(Map<String, Object> raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw != null) raw.forEach((k, v) -> { if (v != null) out.put(k, String.valueOf(v)); });
        return out;
    }

    /** Reads an Azure field that may be a plain value or an {oldValue,newValue} object. */
    private String azureField(JsonNode fields, String key, String defaultValue) {
        JsonNode node = fields.path(key);
        if (node.isMissingNode() || node.isNull()) return defaultValue;
        if (node.isObject() && node.has("newValue")) return node.path("newValue").asText(defaultValue);
        return node.asText(defaultValue);
    }

    /** Infers a requirement type from GitHub issue labels. */
    private String gitHubIssueType(JsonNode issue) {
        JsonNode labels = issue.path("labels");
        if (labels.isArray()) {
            for (JsonNode l : labels) {
                String name = l.path("name").asText("").toLowerCase();
                if (name.contains("epic")) return "EPIC";
                if (name.contains("bug") || name.contains("defect")) return "DEFECT";
                if (name.contains("spike")) return "SPIKE";
            }
        }
        return "STORY";
    }

    private Optional<ProjectIntegrationConfig> findAzureBoardsConfig(String teamProject) {
        return configRepo.findByIntegrationTypeAndEnabled(
                        IntegrationType.AZURE_DEVOPS_BOARDS.name(), true)
                .stream()
                .filter(c -> teamProject.equalsIgnoreCase(c.param("project"))
                        || teamProject.equalsIgnoreCase(c.param("project_key")))
                .findFirst();
    }

    private Optional<ProjectIntegrationConfig> findGitHubIssuesConfig(String repoFullName) {
        return configRepo.findByIntegrationTypeAndEnabled(
                        IntegrationType.GITHUB_ISSUES.name(), true)
                .stream()
                .filter(c -> repoFullName.equalsIgnoreCase(c.param("repository"))
                        || repoFullName.equalsIgnoreCase(joinOwnerRepo(c)))
                .findFirst();
    }

    private String joinOwnerRepo(ProjectIntegrationConfig c) {
        String owner = c.param("owner");
        String repo  = c.param("repo");
        return (owner != null && repo != null) ? owner + "/" + repo : null;
    }

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
