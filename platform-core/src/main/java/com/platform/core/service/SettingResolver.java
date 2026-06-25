package com.platform.core.service;

import com.platform.core.domain.ScopedSetting;
import com.platform.core.domain.ScopedSetting.Scope;
import com.platform.core.repository.PlatformSettingRepository;
import com.platform.core.repository.ProjectRepository;
import com.platform.core.repository.ScopedSettingRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves an effective setting value by deep-merging the ORG, TEAM and PROJECT scopes with
 * precedence PROJECT &gt; TEAM &gt; ORG.
 *
 * <p>ORG defaults live in {@code platform_settings} (the existing global table); TEAM/PROJECT
 * overrides live in {@code scoped_settings}. This mirrors {@link CredentialResolver} but for plain
 * key/value settings (e.g. the AI provider/model/enable flags).
 */
@Service
public class SettingResolver {

  private final PlatformSettingRepository orgRepo;
  private final ScopedSettingRepository scopedRepo;
  private final ProjectRepository projectRepo;

  public SettingResolver(
      PlatformSettingRepository orgRepo,
      ScopedSettingRepository scopedRepo,
      ProjectRepository projectRepo) {
    this.orgRepo = orgRepo;
    this.scopedRepo = scopedRepo;
    this.projectRepo = projectRepo;
  }

  /**
   * Effective value for a project: PROJECT override → org-wide default ({@code platform_settings}).
   * ADO-first hierarchy Org → Project → Team.
   */
  @Transactional(readOnly = true)
  public Optional<String> resolve(UUID projectId, String key) {
    return resolve(projectId, null, key);
  }

  /** Effective value with an optional team override: TEAM → PROJECT → org default. */
  @Transactional(readOnly = true)
  public Optional<String> resolve(UUID projectId, UUID teamId, String key) {
    if (teamId != null) {
      Optional<String> team = scoped(Scope.TEAM, teamId, key);
      if (team.isPresent()) return team;
    }
    if (projectId != null) {
      Optional<String> project = scoped(Scope.PROJECT, projectId, key);
      if (project.isPresent()) return project;
    }
    return org(key);
  }

  /** Effective value for a project with a fallback default. */
  @Transactional(readOnly = true)
  public String resolveOrDefault(UUID projectId, String key, String defaultValue) {
    return resolve(projectId, key).filter(v -> !v.isBlank()).orElse(defaultValue);
  }

  /** Upserts a scoped override (TEAM or PROJECT). */
  @Transactional
  public void set(Scope scope, UUID scopeId, String key, String value) {
    ScopedSetting existing =
        scopedRepo.findByScopeAndScopeIdAndKey(scope.name(), scopeId, key).orElse(null);
    if (existing == null) {
      scopedRepo.save(new ScopedSetting(scope, scopeId, key, value));
    } else {
      existing.setValue(value);
      scopedRepo.save(existing);
    }
  }

  private Optional<String> scoped(Scope scope, UUID scopeId, String key) {
    return scopedRepo
        .findByScopeAndScopeIdAndKey(scope.name(), scopeId, key)
        .map(ScopedSetting::getValue)
        .filter(v -> v != null && !v.isBlank());
  }

  private Optional<String> org(String key) {
    return orgRepo.findById(key).map(s -> s.getValue()).filter(v -> v != null && !v.isBlank());
  }
}
