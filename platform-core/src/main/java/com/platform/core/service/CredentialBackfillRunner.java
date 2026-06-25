package com.platform.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.IntegrationConfig;
import com.platform.core.domain.IntegrationCredential;
import com.platform.core.domain.IntegrationCredential.Scope;
import com.platform.core.domain.PlatformSetting;
import com.platform.core.domain.ProjectIntegrationConfig;
import com.platform.core.repository.IntegrationConfigRepository;
import com.platform.core.repository.IntegrationCredentialRepository;
import com.platform.core.repository.PlatformSettingRepository;
import com.platform.core.repository.ProjectIntegrationConfigRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time, idempotent backfill of the legacy plaintext configs ({@code integration_configs},
 * {@code project_integration_configs}) into the new encrypted {@link IntegrationCredential} model.
 *
 * <p>Runs <strong>automatically</strong> on first deployment: the runner checks {@code
 * platform_settings} for the key {@code system.backfill.v1.completed}. If absent it runs the
 * migration, then sets the flag so subsequent restarts are instant no-ops. Requires {@code
 * PLATFORM_CRED_KEY}; skips with a warning otherwise.
 *
 * <p>The flag can be cleared to force a re-run, or the runner can be disabled explicitly via {@code
 * platform.cred.backfill.enabled=false}.
 */
@Component
public class CredentialBackfillRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(CredentialBackfillRunner.class);
  private static final String COMPLETION_FLAG = "system.backfill.v1.completed";

  /** Connection-param keys treated as secret and moved into the encrypted blob. */
  private static final Set<String> SECRET_KEYS =
      Set.of(
          "apitoken",
          "api_token",
          "token",
          "pat",
          "password",
          "secret",
          "clientsecret",
          "client_secret",
          "apikey",
          "api_key",
          "accesstoken",
          "access_token");

  /** Legacy tracker_type -> new IntegrationType name for lifecycle trackers. */
  private static final Map<String, String> TRACKER_TYPE_MAP =
      Map.of(
          "JIRA", "JIRA_CLOUD",
          "GITHUB", "GITHUB_ISSUES",
          "AZURE", "AZURE_DEVOPS_BOARDS",
          "AZURE_BOARDS", "AZURE_DEVOPS_BOARDS");

  private final IntegrationConfigRepository teamConfigRepo;
  private final ProjectIntegrationConfigRepository projectConfigRepo;
  private final IntegrationCredentialRepository credRepo;
  private final PlatformSettingRepository settingRepo;
  private final CredentialCipher cipher;
  private final ObjectMapper objectMapper;

  public CredentialBackfillRunner(
      IntegrationConfigRepository teamConfigRepo,
      ProjectIntegrationConfigRepository projectConfigRepo,
      IntegrationCredentialRepository credRepo,
      PlatformSettingRepository settingRepo,
      CredentialCipher cipher,
      ObjectMapper objectMapper) {
    this.teamConfigRepo = teamConfigRepo;
    this.projectConfigRepo = projectConfigRepo;
    this.credRepo = credRepo;
    this.settingRepo = settingRepo;
    this.cipher = cipher;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (settingRepo
        .findById(COMPLETION_FLAG)
        .map(s -> "true".equalsIgnoreCase(s.getValue()))
        .orElse(false)) {
      log.debug("[CredBackfill] Already completed on a prior deployment — skipping.");
      return;
    }
    if (!cipher.isConfigured()) {
      log.warn(
          "[CredBackfill] Skipping: PLATFORM_CRED_KEY not configured. "
              + "Set it and restart to encrypt legacy plaintext credentials.");
      return;
    }
    int created = 0;
    try {
      created += backfillTeamConfigs();
      created += backfillProjectConfigs();
      settingRepo.save(new PlatformSetting(COMPLETION_FLAG, "true"));
      log.info("[CredBackfill] Completed. {} credential row(s) created.", created);
    } catch (Exception e) {
      log.error("[CredBackfill] Backfill failed: {}", e.getMessage(), e);
    }
  }

  private int backfillTeamConfigs() {
    int n = 0;
    for (IntegrationConfig cfg : teamConfigRepo.findByEnabledTrue()) {
      String type = TRACKER_TYPE_MAP.getOrDefault(cfg.getTrackerType(), cfg.getTrackerType());
      if (credRepo
          .findByScopeAndScopeIdAndIntegrationType(Scope.TEAM.name(), cfg.getTeamId(), type)
          .isPresent()) {
        continue; // already migrated
      }
      Split split = split(cfg.getConfigJson());
      if (cfg.getProjectKey() != null)
        split.nonSecret.putIfAbsent("project_key", cfg.getProjectKey());

      IntegrationCredential cred =
          new IntegrationCredential(
              Scope.TEAM,
              cfg.getTeamId(),
              type,
              "Migrated " + cfg.getTrackerType(),
              cfg.getBaseUrl(),
              split.nonSecret,
              encryptSecret(split.secret));
      cred.setCreatedBy("backfill");
      credRepo.save(cred);
      n++;
    }
    return n;
  }

  private int backfillProjectConfigs() {
    int n = 0;
    for (ProjectIntegrationConfig cfg : projectConfigRepo.findAll()) {
      UUID projectId = cfg.getProjectId();
      String type = cfg.getIntegrationType();
      if (credRepo
          .findByScopeAndScopeIdAndIntegrationType(Scope.PROJECT.name(), projectId, type)
          .isPresent()) {
        continue;
      }
      Map<String, String> params =
          cfg.getConnectionParams() == null ? Map.of() : cfg.getConnectionParams();
      Split split = split(params);
      if (split.secret.isEmpty()) continue; // nothing secret to protect; leave PIC as-is

      IntegrationCredential cred =
          new IntegrationCredential(
              Scope.PROJECT,
              projectId,
              type,
              cfg.getDisplayName(),
              split.nonSecret.get("base_url"),
              split.nonSecret,
              encryptSecret(split.secret));
      cred.setCreatedBy("backfill");
      credRepo.save(cred);
      n++;
    }
    return n;
  }

  private String encryptSecret(Map<String, String> secret) {
    if (secret.isEmpty()) return null;
    try {
      return cipher.encrypt(objectMapper.writeValueAsString(secret));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize secret for encryption", e);
    }
  }

  private Split split(Map<String, String> all) {
    Split s = new Split();
    if (all == null) return s;
    all.forEach(
        (k, v) -> {
          String norm = k == null ? "" : k.toLowerCase().replace("-", "_");
          if (SECRET_KEYS.contains(norm)) {
            if (v != null) s.secret.put(k, v);
          } else {
            s.nonSecret.put(k, v);
          }
        });
    return s;
  }

  private static final class Split {
    final Map<String, String> nonSecret = new LinkedHashMap<>();
    final Map<String, String> secret = new LinkedHashMap<>();
  }
}
