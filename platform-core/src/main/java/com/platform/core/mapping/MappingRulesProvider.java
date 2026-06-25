package com.platform.core.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.MappingRuleset;
import com.platform.core.domain.Project;
import com.platform.core.repository.MappingRulesetRepository;
import com.platform.core.repository.ProjectRepository;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the {@link MappingRules} used by the Mapping Suggester (ingestion) and the sync-time
 * {@link MappingProfileApplier} (agent), deep by scope: <b>PROJECT override → ORG override →
 * built-in default</b>.
 *
 * <p>The built-in default ships as the classpath resource {@code mapping-rules.json} and can be
 * replaced at deploy time via {@code platform.mapping.rules-path}. ORG and PROJECT overrides are
 * persisted ({@link MappingRuleset}) and edited via the portal; deleting an override "resets to
 * default" (falls back to the parent scope).
 */
@Component
public class MappingRulesProvider {

  private static final Logger log = LoggerFactory.getLogger(MappingRulesProvider.class);
  private static final String DEFAULT_RESOURCE = "mapping-rules.json";

  private final ObjectMapper mapper;
  private final MappingRulesetRepository rulesetRepo;
  private final ProjectRepository projectRepo;
  private final MappingRules builtInDefault;

  public MappingRulesProvider(
      ObjectMapper mapper,
      MappingRulesetRepository rulesetRepo,
      ProjectRepository projectRepo,
      @Value("${platform.mapping.rules-path:}") String overridePath) {
    this.mapper = mapper;
    this.rulesetRepo = rulesetRepo;
    this.projectRepo = projectRepo;
    MappingRules loaded = loadDefault(mapper);
    if (overridePath != null && !overridePath.isBlank()) {
      loaded = loadFileOverride(mapper, overridePath, loaded);
    }
    this.builtInDefault = loaded;
    log.info(
        "[MappingRules] Built-in default loaded: {} lane rule(s), {} field heuristic(s){}",
        size(loaded.laneRules()),
        size(loaded.fieldHeuristics()),
        (overridePath != null && !overridePath.isBlank())
            ? " (file override: " + overridePath + ")"
            : "");
  }

  // ── effective resolution ────────────────────────────────────────────────────

  /** Effective rules for a project: PROJECT override → ORG override → built-in default. */
  @Transactional(readOnly = true)
  public MappingRules effectiveForProject(UUID projectId) {
    MappingRules project = stored(MappingRuleset.Scope.PROJECT, projectId);
    if (project != null) return project;
    UUID orgId =
        projectRepo
            .findById(projectId)
            .map(Project::getOrganization)
            .map(o -> o.getId())
            .orElse(null);
    if (orgId != null) {
      MappingRules org = stored(MappingRuleset.Scope.ORG, orgId);
      if (org != null) return org;
    }
    return builtInDefault;
  }

  /** Effective rules for an organization: ORG override → built-in default. */
  @Transactional(readOnly = true)
  public MappingRules effectiveForOrg(UUID orgId) {
    MappingRules org = stored(MappingRuleset.Scope.ORG, orgId);
    return org != null ? org : builtInDefault;
  }

  private MappingRules stored(MappingRuleset.Scope scope, UUID scopeId) {
    return rulesetRepo
        .findByScopeAndScopeId(scope.name(), scopeId)
        .map(
            rs -> {
              try {
                return parse(rs.getRulesJson());
              } catch (Exception e) {
                log.error(
                    "[MappingRules] Stored {} {} override is invalid — falling back: {}",
                    scope,
                    scopeId,
                    e.getMessage());
                return null;
              }
            })
        .orElse(null);
  }

  // ── default + (de)serialization ──────────────────────────────────────────────

  public MappingRules builtInDefault() {
    return builtInDefault;
  }

  public String defaultJson() {
    return toJson(builtInDefault);
  }

  /** Parses + validates a rules document; throws {@link IllegalArgumentException} if malformed. */
  public MappingRules parse(String json) {
    if (json == null || json.isBlank()) {
      throw new IllegalArgumentException("Mapping rules JSON is empty");
    }
    try {
      return mapper.readValue(json, MappingRules.class);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid mapping rules JSON: " + e.getMessage());
    }
  }

  public String toJson(MappingRules rules) {
    try {
      return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rules);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize mapping rules", e);
    }
  }

  public static MappingRules loadDefault(ObjectMapper mapper) {
    try (InputStream in = new ClassPathResource(DEFAULT_RESOURCE).getInputStream()) {
      return mapper.readValue(in, MappingRules.class);
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to load default mapping rules (" + DEFAULT_RESOURCE + ")", e);
    }
  }

  private static MappingRules loadFileOverride(
      ObjectMapper mapper, String path, MappingRules fallback) {
    try {
      Path p = Path.of(path);
      if (!Files.exists(p)) {
        log.warn("[MappingRules] Override file not found at {} — using bundled default", path);
        return fallback;
      }
      try (InputStream in = Files.newInputStream(p)) {
        return mapper.readValue(in, MappingRules.class);
      }
    } catch (Exception e) {
      log.error(
          "[MappingRules] Failed to read override {} — using bundled default: {}",
          path,
          e.getMessage());
      return fallback;
    }
  }

  private static int size(java.util.List<?> l) {
    return l == null ? 0 : l.size();
  }
}
