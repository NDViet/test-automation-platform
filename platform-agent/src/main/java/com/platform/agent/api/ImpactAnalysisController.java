package com.platform.agent.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.node.tools.GitHubApiClient;
import com.platform.core.domain.ImpactAnalysis;
import com.platform.core.domain.ProjectIntegrationConfig;
import com.platform.core.repository.ImpactAnalysisRepository;
import com.platform.core.repository.ProjectIntegrationConfigRepository;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Impact Analysis hub endpoints for the agent service.
 *
 * <p>GET /hub/impact/{projectId} — list all analyses for a project GET /hub/impact/{projectId}/prs
 * — list open PRs from CODEBASE GitHub repos POST /hub/impact/{projectId} — create analysis +
 * trigger async AI run GET /hub/impact/{projectId}/{id} — get a specific analysis
 */
@RestController
@RequestMapping("/hub/impact")
public class ImpactAnalysisController {

  private static final Logger log = LoggerFactory.getLogger(ImpactAnalysisController.class);

  private final ImpactAnalysisRepository impactRepo;
  private final ProjectIntegrationConfigRepository configRepo;
  private final GitHubApiClient gitHubApiClient;
  private final ImpactAnalysisService impactAnalysisService;
  private final ObjectMapper mapper;

  public ImpactAnalysisController(
      ImpactAnalysisRepository impactRepo,
      ProjectIntegrationConfigRepository configRepo,
      GitHubApiClient gitHubApiClient,
      ImpactAnalysisService impactAnalysisService,
      ObjectMapper mapper) {
    this.impactRepo = impactRepo;
    this.configRepo = configRepo;
    this.gitHubApiClient = gitHubApiClient;
    this.impactAnalysisService = impactAnalysisService;
    this.mapper = mapper;
  }

  // ── List analyses ─────────────────────────────────────────────────────────

  @GetMapping("/{projectId}")
  public ResponseEntity<List<ImpactAnalysis>> list(@PathVariable UUID projectId) {
    return ResponseEntity.ok(impactRepo.findByProjectIdOrderByCreatedAtDesc(projectId));
  }

  // ── List open PRs from CODEBASE GitHub repos ──────────────────────────────

  @GetMapping("/{projectId}/prs")
  public ResponseEntity<List<Map<String, Object>>> listOpenPrs(@PathVariable UUID projectId) {
    List<ProjectIntegrationConfig> configs =
        configRepo.findByProjectIdAndIntegrationTypeAndRepoTypeAndEnabled(
            projectId, "GITHUB", "CODEBASE", true);

    List<Map<String, Object>> result = new ArrayList<>();
    for (ProjectIntegrationConfig config : configs) {
      String repoFullName = config.param("repoFullName");
      String token = config.param("token");
      if (repoFullName == null || repoFullName.isBlank()) continue;

      String[] parts = repoFullName.split("/", 2);
      if (parts.length != 2) continue;
      String owner = parts[0];
      String repo = parts[1];

      try {
        String json = gitHubApiClient.getOpenPRs(owner, repo, token);
        List<JsonNode> prs = mapper.readValue(json, new TypeReference<List<JsonNode>>() {});
        for (JsonNode pr : prs) {
          Map<String, Object> entry = new LinkedHashMap<>();
          entry.put("number", pr.path("number").asInt());
          entry.put("title", pr.path("title").asText(""));
          entry.put("html_url", pr.path("html_url").asText(""));
          entry.put("state", pr.path("state").asText(""));
          entry.put("user", pr.path("user").path("login").asText(""));
          entry.put("updated_at", pr.path("updated_at").asText(""));
          entry.put("head_ref", pr.path("head").path("ref").asText(""));
          entry.put("base_ref", pr.path("base").path("ref").asText(""));
          String body = pr.path("body").asText("");
          entry.put("body", body.length() > 500 ? body.substring(0, 500) : body);
          entry.put("repoFullName", repoFullName);
          result.add(entry);
        }
      } catch (Exception e) {
        log.warn("listOpenPrs: failed to fetch PRs for {}/{}: {}", owner, repo, e.getMessage());
      }
    }

    return ResponseEntity.ok(result);
  }

  // ── Create analysis + trigger async AI run ────────────────────────────────

  @PostMapping("/{projectId}")
  public ResponseEntity<ImpactAnalysis> create(
      @PathVariable UUID projectId, @RequestBody CreateImpactAnalysisRequest request) {

    ImpactAnalysis entity = new ImpactAnalysis();
    entity.setProjectId(projectId);
    entity.setName(
        request.name() != null && !request.name().isBlank() ? request.name() : "Impact Analysis");
    entity.setStatus("RUNNING");

    // Convert linkedPrs list-of-records to List<Map<String,Object>>
    List<Map<String, Object>> linkedPrsMaps = new ArrayList<>();
    if (request.linkedPrs() != null) {
      for (LinkedPrRef ref : request.linkedPrs()) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("repoFullName", ref.repoFullName());
        m.put("prNumber", ref.prNumber());
        m.put("prUrl", ref.prUrl());
        m.put("prTitle", ref.prTitle());
        linkedPrsMaps.add(m);
      }
    }
    entity.setLinkedPrs(linkedPrsMaps);
    entity.setLinkedRequirementIds(
        request.linkedRequirementIds() != null ? request.linkedRequirementIds() : List.of());

    ImpactAnalysis saved = impactRepo.save(entity);

    // Trigger async analysis
    impactAnalysisService.runAnalysis(saved.getId(), projectId);

    return ResponseEntity.status(201).body(saved);
  }

  // ── Get specific analysis ─────────────────────────────────────────────────

  @GetMapping("/{projectId}/{id}")
  public ResponseEntity<ImpactAnalysis> get(@PathVariable UUID projectId, @PathVariable UUID id) {
    return impactRepo
        .findById(id)
        .filter(a -> projectId.equals(a.getProjectId()))
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  // ── Request / inner types ─────────────────────────────────────────────────

  public record CreateImpactAnalysisRequest(
      String name, List<LinkedPrRef> linkedPrs, List<String> linkedRequirementIds) {}

  public record LinkedPrRef(String repoFullName, int prNumber, String prUrl, String prTitle) {}
}
