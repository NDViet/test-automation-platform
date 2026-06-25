package com.platform.ingestion.management.discovery;

import com.platform.core.domain.MappingRuleset;
import com.platform.core.domain.MappingRuleset.Scope;
import com.platform.core.mapping.MappingRulesProvider;
import com.platform.core.repository.MappingRulesetRepository;
import com.platform.core.repository.OrganizationRepository;
import com.platform.core.repository.ProjectRepository;
import com.platform.ingestion.management.discovery.dto.MappingRulesetView;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Manages mapping-rule overrides at ORG and PROJECT scope. Save validates the JSON against the
 * {@link MappingRules} schema; reset deletes the override so resolution falls back to the parent
 * (PROJECT → ORG → built-in default).
 */
@Service
@Transactional
public class MappingRulesService {

  private final MappingRulesetRepository repo;
  private final MappingRulesProvider provider;
  private final OrganizationRepository orgRepo;
  private final ProjectRepository projectRepo;

  public MappingRulesService(
      MappingRulesetRepository repo,
      MappingRulesProvider provider,
      OrganizationRepository orgRepo,
      ProjectRepository projectRepo) {
    this.repo = repo;
    this.provider = provider;
    this.orgRepo = orgRepo;
    this.projectRepo = projectRepo;
  }

  /** The built-in default ruleset (read-only baseline for "Reset to default"). */
  @Transactional(readOnly = true)
  public MappingRulesetView getDefault() {
    return new MappingRulesetView("DEFAULT", false, "DEFAULT", provider.defaultJson(), null, null);
  }

  // ── ORG ──────────────────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public MappingRulesetView getOrg(UUID orgId) {
    requireOrg(orgId);
    return repo.findByScopeAndScopeId(Scope.ORG.name(), orgId)
        .map(
            rs ->
                new MappingRulesetView(
                    "ORG", true, "ORG", rs.getRulesJson(), rs.getUpdatedBy(), rs.getUpdatedAt()))
        .orElseGet(
            () ->
                new MappingRulesetView(
                    "ORG", false, "DEFAULT", provider.defaultJson(), null, null));
  }

  public MappingRulesetView saveOrg(UUID orgId, String json, String actor) {
    requireOrg(orgId);
    return new ViewFrom(upsert(Scope.ORG, orgId, json, actor)).asOrg();
  }

  public void resetOrg(UUID orgId) {
    requireOrg(orgId);
    repo.deleteByScopeAndScopeId(Scope.ORG.name(), orgId);
  }

  // ── PROJECT ───────────────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public MappingRulesetView getProject(UUID projectId) {
    requireProject(projectId);
    var own = repo.findByScopeAndScopeId(Scope.PROJECT.name(), projectId);
    if (own.isPresent()) {
      var rs = own.get();
      return new MappingRulesetView(
          "PROJECT", true, "PROJECT", rs.getRulesJson(), rs.getUpdatedBy(), rs.getUpdatedAt());
    }
    // Inheriting — show what is currently effective (ORG override or built-in default).
    UUID orgId = projectRepo.findById(projectId).map(p -> p.getOrganization().getId()).orElse(null);
    boolean orgCustom = orgId != null && repo.existsByScopeAndScopeId(Scope.ORG.name(), orgId);
    String source = orgCustom ? "ORG" : "DEFAULT";
    String json = provider.toJson(provider.effectiveForProject(projectId));
    return new MappingRulesetView("PROJECT", false, source, json, null, null);
  }

  public MappingRulesetView saveProject(UUID projectId, String json, String actor) {
    requireProject(projectId);
    return new ViewFrom(upsert(Scope.PROJECT, projectId, json, actor)).asProject();
  }

  public void resetProject(UUID projectId) {
    requireProject(projectId);
    repo.deleteByScopeAndScopeId(Scope.PROJECT.name(), projectId);
  }

  // ── helpers ────────────────────────────────────────────────────────────────────

  private MappingRuleset upsert(Scope scope, UUID scopeId, String json, String actor) {
    // Validate + normalize (pretty, canonical) before storing.
    String normalized;
    try {
      normalized = provider.toJson(provider.parse(json));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
    return repo.findByScopeAndScopeId(scope.name(), scopeId)
        .map(
            rs -> {
              rs.setRulesJson(normalized);
              rs.setUpdatedBy(actor);
              return repo.save(rs);
            })
        .orElseGet(() -> repo.save(new MappingRuleset(scope, scopeId, normalized, actor)));
  }

  private void requireOrg(UUID orgId) {
    if (!orgRepo.existsById(orgId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found: " + orgId);
    }
  }

  private void requireProject(UUID projectId) {
    if (!projectRepo.existsById(projectId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + projectId);
    }
  }

  private record ViewFrom(MappingRuleset rs) {
    MappingRulesetView asOrg() {
      return view("ORG");
    }

    MappingRulesetView asProject() {
      return view("PROJECT");
    }

    private MappingRulesetView view(String scope) {
      return new MappingRulesetView(
          scope, true, scope, rs.getRulesJson(), rs.getUpdatedBy(), rs.getUpdatedAt());
    }
  }
}
