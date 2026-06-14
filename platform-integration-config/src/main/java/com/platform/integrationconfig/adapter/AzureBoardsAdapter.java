package com.platform.integrationconfig.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.integration.*;
import com.platform.common.model.RequirementRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Tier-1 IntegrationAdapter for Azure DevOps Boards work items.
 *
 * <p>Maps work items to {@link RequirementRecord}s and handles Service Hook
 * payloads. Credentials: {@code connectionParams.pat} (+ {@code organization},
 * {@code project}, optional {@code base_url}).</p>
 */
@Component
public class AzureBoardsAdapter
        implements IntegrationAdapter<RequirementRecord, AzureBoardsAdapter.AzureCommand> {

    private static final Logger log = LoggerFactory.getLogger(AzureBoardsAdapter.class);
    private static final String API = "api-version=7.0";

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper mapper;

    public AzureBoardsAdapter(RestClient.Builder restClientBuilder, ObjectMapper mapper) {
        this.restClientBuilder = restClientBuilder;
        this.mapper            = mapper;
    }

    @Override
    public IntegrationType type() {
        return IntegrationType.AZURE_DEVOPS_BOARDS;
    }

    @Override
    public PagedRecords<RequirementRecord> fetchPage(SourceIntegrationConfig config, SyncCursor cursor) {
        try {
            RestClient client = client(config);
            String project = project(config);
            String wiql = "SELECT [System.Id] FROM workitems WHERE [System.TeamProject] = '"
                    + project.replace("'", "''") + "' ORDER BY [System.ChangedDate] DESC";
            JsonNode q = client.post()
                    .uri("/{p}/_apis/wit/wiql?" + API, project)
                    .header("Content-Type", "application/json")
                    .body(Map.of("query", wiql))
                    .retrieve().body(JsonNode.class);

            List<RequirementRecord> out = new ArrayList<>();
            if (q != null) {
                for (JsonNode wi : q.path("workItems")) {
                    JsonNode item = client.get()
                            .uri("/{p}/_apis/wit/workitems/{id}?" + API, project, wi.path("id").asText())
                            .retrieve().body(JsonNode.class);
                    if (item != null) out.add(toRecord(item.path("id").asText(), item.path("fields")));
                }
            }
            return new PagedRecords<>(out, cursor.advance(Instant.now(), null));
        } catch (Exception e) {
            log.warn("[AzureBoardsAdapter] fetchPage failed: {}", e.getMessage());
            return PagedRecords.empty(cursor.advance(Instant.now(), null));
        }
    }

    @Override
    public String push(SourceIntegrationConfig config, AzureCommand command) {
        // Create-only minimal push; lifecycle create/close lives in platform-integration.
        RestClient client = client(config);
        String project = project(config);
        List<Map<String, Object>> patch = List.of(
                Map.of("op", "add", "path", "/fields/System.Title", "value", command.title()),
                Map.of("op", "add", "path", "/fields/System.Description", "value",
                        command.description() == null ? "" : command.description()));
        JsonNode resp = client.post()
                .uri("/{p}/_apis/wit/workitems/${t}?" + API, project, command.workItemType())
                .header("Content-Type", "application/json-patch+json")
                .body(patch)
                .retrieve().body(JsonNode.class);
        return resp == null ? null : resp.path("id").asText(null);
    }

    @Override
    public IntegrationHealth healthCheck(SourceIntegrationConfig config) {
        try {
            client(config).get().uri("/_apis/projects?" + API).retrieve().body(String.class);
            return IntegrationHealth.healthy();
        } catch (Exception e) {
            return IntegrationHealth.down("Azure Boards health check failed: " + e.getMessage(), 1);
        }
    }

    @Override
    public List<RequirementRecord> fromWebhook(WebhookEvent event, SourceIntegrationConfig config) {
        JsonNode root = mapper.valueToTree(event.rawPayload());
        JsonNode resource = root.path("resource");
        JsonNode fields = resource.path("revision").path("fields");
        if (fields.isMissingNode() || fields.isEmpty()) fields = resource.path("fields");
        String id = resource.path("id").asText(resource.path("workItemId").asText(""));
        if (id.isBlank()) return List.of();
        return List.of(toRecord(id, fields));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private RequirementRecord toRecord(String externalId, JsonNode fields) {
        String title = azureField(fields, "System.Title", "(no title)");
        String desc  = azureField(fields, "System.Description", "");
        String type  = azureField(fields, "System.WorkItemType", "STORY");
        String status = azureField(fields, "System.State", "OPEN");
        return new RequirementRecord(
                null, externalId, IntegrationType.AZURE_DEVOPS_BOARDS,
                title, desc, List.of(), issueType(type), status, null,
                List.of(), null, externalId, 0, Instant.now());
    }

    private String azureField(JsonNode fields, String key, String def) {
        JsonNode n = fields.path(key);
        if (n.isMissingNode() || n.isNull()) return def;
        if (n.isObject() && n.has("newValue")) return n.path("newValue").asText(def);
        return n.asText(def);
    }

    private RequirementRecord.IssueType issueType(String raw) {
        return switch (raw.toUpperCase()) {
            case "EPIC" -> RequirementRecord.IssueType.EPIC;
            case "BUG", "DEFECT" -> RequirementRecord.IssueType.DEFECT;
            case "TASK" -> RequirementRecord.IssueType.TASK;
            case "SPIKE" -> RequirementRecord.IssueType.SPIKE;
            default -> RequirementRecord.IssueType.STORY;
        };
    }

    private RestClient client(SourceIntegrationConfig config) {
        String base = config.param("base_url");
        if (base == null || base.isBlank()) base = "https://dev.azure.com";
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String org = config.param("organization");
        if (org != null && !base.endsWith("/" + org)) base = base + "/" + org;
        String pat = config.param("pat");
        String auth = "Basic " + Base64.getEncoder()
                .encodeToString((":" + (pat == null ? "" : pat)).getBytes(StandardCharsets.UTF_8));
        return restClientBuilder.clone().baseUrl(base)
                .defaultHeader("Authorization", auth)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    private String project(SourceIntegrationConfig config) {
        String p = config.param("project");
        return p != null ? p : config.param("project_key");
    }

    /** Minimal create command for the push path. */
    public record AzureCommand(String workItemType, String title, String description) {}
}
