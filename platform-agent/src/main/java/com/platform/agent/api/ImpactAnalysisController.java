package com.platform.agent.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.node.tools.GitHubApiClient;
import com.platform.core.domain.ImpactAnalysis;
import com.platform.core.domain.ProjectIntegrationConfig;
import com.platform.core.domain.ProjectRepoAssignment;
import com.platform.core.repository.ImpactAnalysisRepository;
import com.platform.core.repository.ProjectIntegrationConfigRepository;
import com.platform.core.repository.ProjectRepoAssignmentRepository;
import com.platform.core.service.CredentialResolver;
import com.platform.core.service.ResolvedCredential;
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
  private final ProjectRepoAssignmentRepository assignmentRepo;
  private final CredentialResolver credentialResolver;
  private final GitHubApiClient gitHubApiClient;
  private final ImpactAnalysisService impactAnalysisService;
  private final ObjectMapper mapper;

  public ImpactAnalysisController(
      ImpactAnalysisRepository impactRepo,
      ProjectIntegrationConfigRepository configRepo,
      ProjectRepoAssignmentRepository assignmentRepo,
      CredentialResolver credentialResolver,
      GitHubApiClient gitHubApiClient,
      ImpactAnalysisService impactAnalysisService,
      ObjectMapper mapper) {
    this.impactRepo = impactRepo;
    this.configRepo = configRepo;
    this.assignmentRepo = assignmentRepo;
    this.credentialResolver = credentialResolver;
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
    List<Map<String, Object>> result = new ArrayList<>();
    // A repo can be tagged Codebase in two independent surfaces; de-dupe by repoFullName so a repo
    // present in both is listed once.
    Set<String> seen = new HashSet<>();

    // Source 1 — GITHUB integrations tagged repoType=CODEBASE (Project Settings → Integrations).
    // Token comes from the config's own params (existing behaviour, unchanged).
    List<ProjectIntegrationConfig> configs =
        configRepo.findByProjectIdAndIntegrationTypeAndRepoTypeAndEnabled(
            projectId, "GITHUB", "CODEBASE", true);
    for (ProjectIntegrationConfig config : configs) {
      String repoFullName = config.param("repoFullName");
      if (repoFullName == null || repoFullName.isBlank()) continue;
      if (!seen.add(repoFullName)) continue;
      fetchOpenPrs(repoFullName, config.param("token"), result);
    }

    // Source 2 — repos assigned role=CODEBASE via Project Settings → GitHub
    // (ProjectRepoAssignment).
    // The assignment stores no token; resolve the effective GitHub PAT via the Org→Project→Team
    // credential cascade (resolved lazily, once, only if there is a Codebase assignment to fetch).
    List<ProjectRepoAssignment> assignments =
        assignmentRepo.findByProjectIdOrderByRepoFullName(projectId);
    String assignedToken = null;
    boolean tokenResolved = false;
    for (ProjectRepoAssignment assignment : assignments) {
      if (!"CODEBASE".equalsIgnoreCase(assignment.getRole())) continue;
      String repoFullName = assignment.getRepoFullName();
      if (repoFullName == null || repoFullName.isBlank()) continue;
      if (!seen.add(repoFullName)) continue;
      if (!tokenResolved) {
        assignedToken = resolveGitHubToken(projectId);
        tokenResolved = true;
      }
      if (assignedToken == null || assignedToken.isBlank()) {
        log.warn(
            "listOpenPrs: no GitHub token resolved for project {} — skipping Codebase repo {}",
            projectId,
            repoFullName);
        continue;
      }
      fetchOpenPrs(repoFullName, assignedToken, result);
    }

    return ResponseEntity.ok(result);
  }

  /** Fetch open PRs for one "owner/repo" and append mapped entries to {@code result}. */
  private void fetchOpenPrs(String repoFullName, String token, List<Map<String, Object>> result) {
    String[] parts = repoFullName.split("/", 2);
    if (parts.length != 2) return;
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
      log.warn("listOpenPrs: failed to fetch PRs for {}: {}", repoFullName, e.getMessage());
    }
  }

  /** Effective GitHub PAT (pat, then token) via the Org→Project→Team cascade, or null. */
  private String resolveGitHubToken(UUID projectId) {
    ResolvedCredential cred = credentialResolver.resolve(projectId, "GITHUB").orElse(null);
    if (cred == null) return null;
    String pat = cred.secret("pat");
    return (pat != null && !pat.isBlank()) ? pat : cred.secret("token");
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
    entity.setLinkedPrs(toLinkedPrMaps(request.linkedPrs()));
    entity.setLinkedRequirementIds(
        request.linkedRequirementIds() != null ? request.linkedRequirementIds() : List.of());
    entity.setCustomPrompt(request.customPrompt());

    ImpactAnalysis saved = impactRepo.save(entity);

    // Trigger async analysis
    impactAnalysisService.runAnalysis(saved.getId(), projectId);

    return ResponseEntity.status(201).body(saved);
  }

  // ── Update analysis (replace PRs / requirements / instructions) + re-run ──

  @PutMapping("/{projectId}/{id}")
  public ResponseEntity<ImpactAnalysis> update(
      @PathVariable UUID projectId,
      @PathVariable UUID id,
      @RequestBody CreateImpactAnalysisRequest request) {

    ImpactAnalysis entity =
        impactRepo.findById(id).filter(a -> projectId.equals(a.getProjectId())).orElse(null);
    if (entity == null) return ResponseEntity.notFound().build();
    if ("RUNNING".equals(entity.getStatus())) {
      // Don't clobber an in-flight run.
      return ResponseEntity.status(409).build();
    }

    if (request.name() != null && !request.name().isBlank()) entity.setName(request.name());
    entity.setLinkedPrs(toLinkedPrMaps(request.linkedPrs()));
    entity.setLinkedRequirementIds(
        request.linkedRequirementIds() != null ? request.linkedRequirementIds() : List.of());
    entity.setCustomPrompt(request.customPrompt());
    // Fresh re-run — clear the prior result so the UI shows a clean RUNNING state.
    entity.setStatus("RUNNING");
    entity.setSummary(null);
    entity.setSuggestions(null);

    ImpactAnalysis saved = impactRepo.save(entity);
    impactAnalysisService.runAnalysis(saved.getId(), projectId);

    return ResponseEntity.ok(saved);
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

  /** Convert linked-PR records to the persisted jsonb shape (carries prTitle + prBody). */
  private static List<Map<String, Object>> toLinkedPrMaps(List<LinkedPrRef> refs) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (refs == null) return out;
    for (LinkedPrRef ref : refs) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("repoFullName", ref.repoFullName());
      m.put("prNumber", ref.prNumber());
      m.put("prUrl", ref.prUrl());
      m.put("prTitle", ref.prTitle());
      m.put("prBody", ref.prBody());
      out.add(m);
    }
    return out;
  }

  // ── Request / inner types ─────────────────────────────────────────────────

  public record CreateImpactAnalysisRequest(
      String name,
      List<LinkedPrRef> linkedPrs,
      List<String> linkedRequirementIds,
      String customPrompt) {}

  public record LinkedPrRef(
      String repoFullName, int prNumber, String prUrl, String prTitle, String prBody) {}
}
