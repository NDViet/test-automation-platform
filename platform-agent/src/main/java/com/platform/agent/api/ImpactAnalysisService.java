package com.platform.agent.api;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.node.tools.GitHubApiClient;
import com.platform.core.domain.ImpactAnalysis;
import com.platform.core.domain.PlatformTestCase;
import com.platform.core.domain.ProjectIntegrationConfig;
import com.platform.core.repository.ImpactAnalysisRepository;
import com.platform.core.repository.PlatformSettingRepository;
import com.platform.core.repository.PlatformTestCaseRepository;
import com.platform.core.repository.ProjectIntegrationConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * Async service that drives the Impact Analysis AI workflow:
 * 1. Loads PR file diffs from GitHub for each linked PR.
 * 2. Fetches requirement details from the ingestion service.
 * 3. Calls the Claude API to generate test coverage suggestions.
 * 4. Persists the result back to the ImpactAnalysis entity.
 */
@Service
public class ImpactAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ImpactAnalysisService.class);

    private static final String DB_KEY_ANTHROPIC = "ai.anthropic.api-key";
    private static final String DB_KEY_LEGACY    = "ai.api-key";

    private static final String SYSTEM_PROMPT =
            "You are an Impact Analysis AI. Given PR diffs, requirements, and existing test cases, " +
            "you identify what test coverage needs creating or updating.\n\n" +
            "For UPDATE_MANUAL_TEST suggestions, always set testCaseId to the ID of the specific existing " +
            "test case that needs updating (if one exists). Leave testCaseId null only for new test cases.\n\n" +
            "Respond ONLY with valid JSON matching this schema:\n" +
            "{\n" +
            "  \"summary\": \"2-3 sentence overview\",\n" +
            "  \"suggestions\": [\n" +
            "    {\n" +
            "      \"type\": \"UPDATE_MANUAL_TEST\" | \"CREATE_AUTOMATED_TEST\" | \"UPDATE_AUTOMATION\",\n" +
            "      \"title\": \"short title\",\n" +
            "      \"reason\": \"why this change is needed\",\n" +
            "      \"details\": \"specific changes or test steps to implement\",\n" +
            "      \"testCaseId\": \"uuid-of-existing-test-case-or-null\",\n" +
            "      \"priority\": \"HIGH\" | \"MEDIUM\" | \"LOW\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";

    private final ImpactAnalysisRepository impactRepo;
    private final ProjectIntegrationConfigRepository configRepo;
    private final PlatformTestCaseRepository testCaseRepo;
    private final GitHubApiClient gitHubApiClient;
    private final PlatformSettingRepository settingRepo;
    private final ObjectMapper mapper;
    private final RestClient ingestionClient;

    @Value("${anthropic.api-key:}")
    private String envApiKey;

    // Cached client — rebuilt only when the resolved key changes
    private volatile String cachedKey = null;
    private volatile AnthropicClient client;

    public ImpactAnalysisService(ImpactAnalysisRepository impactRepo,
                                  ProjectIntegrationConfigRepository configRepo,
                                  PlatformTestCaseRepository testCaseRepo,
                                  GitHubApiClient gitHubApiClient,
                                  PlatformSettingRepository settingRepo,
                                  ObjectMapper mapper,
                                  @Value("${portal.services.ingestion:http://localhost:8083}") String ingestionUrl) {
        this.impactRepo       = impactRepo;
        this.configRepo       = configRepo;
        this.testCaseRepo     = testCaseRepo;
        this.gitHubApiClient  = gitHubApiClient;
        this.settingRepo      = settingRepo;
        this.mapper           = mapper;
        this.ingestionClient  = RestClient.builder().baseUrl(ingestionUrl).build();
    }

    @Async
    public void runAnalysis(UUID analysisId, UUID projectId) {
        ImpactAnalysis analysis = impactRepo.findById(analysisId).orElse(null);
        if (analysis == null) {
            log.error("runAnalysis: ImpactAnalysis {} not found", analysisId);
            return;
        }

        try {
            // ── 1. Collect PR file diffs ──────────────────────────────────────────
            List<Map<String, Object>> linkedPrs = analysis.getLinkedPrs();
            if (linkedPrs == null) linkedPrs = List.of();

            List<PrDiffContext> prDiffs = new ArrayList<>();
            for (Map<String, Object> prRef : linkedPrs) {
                String repoFullName = asString(prRef.get("repoFullName"));
                Object prNumberObj  = prRef.get("prNumber");
                if (repoFullName == null || prNumberObj == null) continue;

                int prNumber = ((Number) prNumberObj).intValue();
                String[] parts = repoFullName.split("/", 2);
                if (parts.length != 2) continue;
                String owner = parts[0];
                String repo  = parts[1];

                // Find a matching GitHub CODEBASE config for this repo
                String token = resolveTokenForRepo(projectId, repoFullName);

                try {
                    String filesJson = gitHubApiClient.getPrFiles(owner, repo, prNumber, token);
                    List<Map<String, Object>> files = mapper.readValue(filesJson,
                            new TypeReference<List<Map<String, Object>>>() {});

                    List<String> filenames = files.stream()
                            .map(f -> asString(f.get("filename")))
                            .filter(Objects::nonNull)
                            .toList();

                    prDiffs.add(new PrDiffContext(
                            repoFullName,
                            prNumber,
                            asString(prRef.get("prTitle")),
                            filenames));
                } catch (Exception e) {
                    log.warn("runAnalysis: failed to fetch files for {}/{}#{}: {}", owner, repo, prNumber, e.getMessage());
                }
            }

            // ── 2. Fetch requirements ─────────────────────────────────────────────
            List<String> linkedReqIds = analysis.getLinkedRequirementIds();
            if (linkedReqIds == null) linkedReqIds = List.of();

            List<Map<String, Object>> requirements = fetchRequirements(projectId, linkedReqIds);

            // ── 3. Load existing test cases that cover these requirements ─────────
            List<PlatformTestCase> existingTestCases = loadRelatedTestCases(projectId, linkedReqIds);

            // ── 4. Build Claude prompt ────────────────────────────────────────────
            String userMessage = buildUserMessage(prDiffs, requirements, existingTestCases);

            // ── 4. Call Claude API ────────────────────────────────────────────────
            AnthropicClient claude = getClient();
            if (claude == null) {
                throw new IllegalStateException("Anthropic API key not configured");
            }

            MessageCreateParams params = MessageCreateParams.builder()
                    .model(Model.CLAUDE_SONNET_4_6)
                    .maxTokens(4096)
                    .system(SYSTEM_PROMPT)
                    .addUserMessage(userMessage)
                    .build();

            Message response = claude.messages().create(params);
            String text = response.content().stream()
                    .filter(ContentBlock::isText)
                    .map(b -> b.asText().text())
                    .findFirst()
                    .orElse("");

            // ── 5. Parse response ─────────────────────────────────────────────────
            String jsonText = extractJson(text);
            JsonNode root = mapper.readTree(jsonText);

            String summary = root.path("summary").asText(null);
            Map<String, Object> suggestions = mapper.convertValue(root,
                    new TypeReference<Map<String, Object>>() {});

            // ── 6. Persist result ─────────────────────────────────────────────────
            analysis.setStatus("COMPLETED");
            analysis.setSummary(summary);
            analysis.setSuggestions(suggestions);
            impactRepo.save(analysis);
            log.info("runAnalysis: completed analysis {} for project {}", analysisId, projectId);

        } catch (Exception e) {
            log.error("runAnalysis: failed for analysis {} project {}: {}", analysisId, projectId, e.getMessage(), e);
            try {
                analysis.setStatus("FAILED");
                analysis.setSummary(e.getMessage());
                impactRepo.save(analysis);
            } catch (Exception saveEx) {
                log.error("runAnalysis: also failed to save FAILED status: {}", saveEx.getMessage());
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolveTokenForRepo(UUID projectId, String repoFullName) {
        List<ProjectIntegrationConfig> configs =
                configRepo.findByProjectIdAndIntegrationTypeAndRepoTypeAndEnabled(
                        projectId, "GITHUB", "CODEBASE", true);
        return configs.stream()
                .filter(c -> repoFullName.equals(c.param("repoFullName")))
                .map(c -> c.param("token"))
                .filter(t -> t != null && !t.isBlank())
                .findFirst()
                // Fall back to any enabled GITHUB config token
                .orElseGet(() -> configRepo.findByProjectId(projectId).stream()
                        .filter(c -> "GITHUB".equals(c.getIntegrationType()) && c.isEnabled())
                        .map(c -> c.param("token"))
                        .filter(t -> t != null && !t.isBlank())
                        .findFirst()
                        .orElse(null));
    }

    private List<Map<String, Object>> fetchRequirements(UUID projectId, List<String> linkedReqIds) {
        if (linkedReqIds.isEmpty()) return List.of();
        try {
            String json = ingestionClient.get()
                    .uri("/api/v1/projects/" + projectId + "/requirements")
                    .retrieve()
                    .body(String.class);
            if (json == null || json.isBlank()) return List.of();

            List<Map<String, Object>> all = mapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {});
            Set<String> wantedIds = new HashSet<>(linkedReqIds);
            return all.stream()
                    .filter(r -> {
                        Object rid = r.get("id");
                        return rid != null && wantedIds.contains(rid.toString());
                    })
                    .toList();
        } catch (Exception e) {
            log.warn("fetchRequirements: could not fetch for project {}: {}", projectId, e.getMessage());
            return List.of();
        }
    }

    private List<PlatformTestCase> loadRelatedTestCases(UUID projectId, List<String> linkedReqIds) {
        if (linkedReqIds.isEmpty()) {
            return testCaseRepo.findByProjectIdOrderByCreatedAtDesc(projectId);
        }
        Set<String> reqIdSet = new HashSet<>(linkedReqIds);
        return testCaseRepo.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .filter(tc -> {
                    // Include test cases linked to these requirements
                    if (tc.getSourceRequirementId() != null
                            && reqIdSet.contains(tc.getSourceRequirementId().toString())) {
                        return true;
                    }
                    List<String> linkedIds = tc.getLinkedRequirementIds();
                    if (linkedIds != null) {
                        return linkedIds.stream().anyMatch(reqIdSet::contains);
                    }
                    return false;
                })
                .toList();
    }

    private String buildUserMessage(List<PrDiffContext> prDiffs, List<Map<String, Object>> requirements,
                                     List<PlatformTestCase> existingTestCases) {
        StringBuilder sb = new StringBuilder();

        sb.append("## PR Changes\n");
        if (prDiffs.isEmpty()) {
            sb.append("No PR file diffs available.\n");
        } else {
            for (PrDiffContext pr : prDiffs) {
                sb.append("### ").append(pr.repoFullName())
                  .append(" PR #").append(pr.prNumber());
                if (pr.prTitle() != null && !pr.prTitle().isBlank()) {
                    sb.append(": ").append(pr.prTitle());
                }
                sb.append("\nChanged files:\n");
                if (pr.changedFiles().isEmpty()) {
                    sb.append("(no files)\n");
                } else {
                    pr.changedFiles().forEach(f -> sb.append("- ").append(f).append("\n"));
                }
                sb.append("\n");
            }
        }

        sb.append("\n## Requirements Being Tested\n");
        if (requirements.isEmpty()) {
            sb.append("No linked requirements.\n");
        } else {
            for (Map<String, Object> req : requirements) {
                String title       = asString(req.get("title"));
                String description = asString(req.get("description"));
                Object ac          = req.get("acceptanceCriteria");

                sb.append("### ").append(title != null ? title : "(untitled)").append("\n");
                if (description != null && !description.isBlank()) {
                    sb.append(description.trim()).append("\n");
                }
                if (ac != null) {
                    sb.append("Acceptance criteria: ").append(ac).append("\n");
                }
                sb.append("\n");
            }
        }

        // Existing test cases — critical context for UPDATE_MANUAL_TEST suggestions
        if (!existingTestCases.isEmpty()) {
            sb.append("\n## Existing Test Cases (consider these when suggesting updates)\n");
            for (PlatformTestCase tc : existingTestCases) {
                sb.append("- ID: ").append(tc.getId()).append("\n");
                sb.append("  Title: ").append(tc.getTitle()).append("\n");
                sb.append("  Status: ").append(tc.getStatus())
                  .append(" | Automation: ").append(tc.getAutomationStatus()).append("\n");
                if (tc.getAutomationPrUrl() != null) {
                    sb.append("  Automation PR: ").append(tc.getAutomationPrUrl()).append("\n");
                }
                if (tc.getDescription() != null && !tc.getDescription().isBlank()) {
                    sb.append("  Description: ").append(tc.getDescription().substring(
                            0, Math.min(200, tc.getDescription().length()))).append("…\n");
                }
                sb.append("\n");
            }
        }

        sb.append("\nAnalyze the PR impact on these requirements and existing test cases. " +
                  "Generate specific, actionable suggestions. " +
                  "For UPDATE_MANUAL_TEST suggestions, reference the exact testCaseId from above.");
        return sb.toString();
    }

    /**
     * Extracts a JSON object from a Claude response that may be wrapped in a markdown
     * code block (```json ... ```) or returned as raw JSON.
     */
    private String extractJson(String text) {
        if (text == null || text.isBlank()) return "{}";
        // Strip markdown code fences if present
        int start = text.indexOf("```");
        if (start >= 0) {
            int newline = text.indexOf('\n', start);
            if (newline < 0) return text;
            int end = text.lastIndexOf("```");
            if (end > newline) {
                return text.substring(newline + 1, end).trim();
            }
        }
        // Find the first '{' for raw JSON
        int brace = text.indexOf('{');
        if (brace >= 0) {
            return text.substring(brace).trim();
        }
        return text.trim();
    }

    private String resolveApiKey() {
        String dbKey = settingRepo.findById(DB_KEY_ANTHROPIC)
                .map(s -> s.getValue())
                .filter(v -> v != null && !v.isBlank())
                .orElse(null);
        if (dbKey != null) return dbKey;

        String legacyKey = settingRepo.findById(DB_KEY_LEGACY)
                .map(s -> s.getValue())
                .filter(v -> v != null && !v.isBlank())
                .orElse(null);
        if (legacyKey != null) return legacyKey;

        if (envApiKey != null && !envApiKey.isBlank()) return envApiKey;
        return null;
    }

    private synchronized AnthropicClient getClient() {
        String key = resolveApiKey();
        if (key == null || key.isBlank()) {
            return null;
        }
        if (!key.equals(cachedKey)) {
            log.info("[ImpactAnalysis] Anthropic API key changed — rebuilding client");
            cachedKey = key;
            client = AnthropicOkHttpClient.builder().apiKey(key).build();
        }
        return client;
    }

    private static String asString(Object o) {
        return o != null ? o.toString() : null;
    }

    // ── Internal DTO ──────────────────────────────────────────────────────────

    private record PrDiffContext(String repoFullName, int prNumber, String prTitle,
                                  List<String> changedFiles) {}
}
