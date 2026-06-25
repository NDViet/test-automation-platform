package com.platform.integration.consumer;

import com.platform.common.dto.UnifiedTestResult;
import com.platform.common.kafka.Topics;
import com.platform.core.domain.IntegrationConfig;
import com.platform.core.domain.Organization;
import com.platform.core.domain.Project;
import com.platform.core.domain.Team;
import com.platform.core.repository.IntegrationConfigRepository;
import com.platform.core.repository.OrganizationRepository;
import com.platform.core.repository.ProjectRepository;
import com.platform.core.repository.TeamRepository;
import com.platform.core.service.CredentialResolver;
import com.platform.core.service.ResolvedCredential;
import com.platform.integration.jira.JiraTrackerFactory;
import com.platform.integration.lifecycle.TicketLifecycleManager;
import com.platform.integration.port.IssueTrackerPort;
import com.platform.integration.port.TrackerFactoryRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link UnifiedTestResult} events and drives the ticket lifecycle.
 *
 * <p>Primary path is cascade-driven: for each lifecycle integration type, resolve the effective
 * credential (Org→Team→Project) and run the lifecycle if one exists. If no credentials are
 * configured at all for the project, falls back to the legacy per-team {@link IntegrationConfig}
 * JIRA path so existing installs keep working.
 */
@Component
public class IntegrationEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(IntegrationEventConsumer.class);

  private final OrganizationRepository orgRepo;
  private final TeamRepository teamRepo;
  private final ProjectRepository projectRepo;
  private final IntegrationConfigRepository configRepo;
  private final TicketLifecycleManager lifecycleManager;
  private final JiraTrackerFactory jiraFactory;
  private final CredentialResolver credentialResolver;
  private final TrackerFactoryRegistry trackerRegistry;

  public IntegrationEventConsumer(
      OrganizationRepository orgRepo,
      TeamRepository teamRepo,
      ProjectRepository projectRepo,
      IntegrationConfigRepository configRepo,
      TicketLifecycleManager lifecycleManager,
      JiraTrackerFactory jiraFactory,
      CredentialResolver credentialResolver,
      TrackerFactoryRegistry trackerRegistry) {
    this.orgRepo = orgRepo;
    this.teamRepo = teamRepo;
    this.projectRepo = projectRepo;
    this.configRepo = configRepo;
    this.lifecycleManager = lifecycleManager;
    this.jiraFactory = jiraFactory;
    this.credentialResolver = credentialResolver;
    this.trackerRegistry = trackerRegistry;
  }

  @KafkaListener(
      topics = Topics.TEST_RESULTS_RAW,
      groupId = "${spring.kafka.consumer.group-id:platform-integration}",
      containerFactory = "kafkaListenerContainerFactory")
  public void consume(ConsumerRecord<String, UnifiedTestResult> record) {
    UnifiedTestResult result = record.value();
    if (result == null) return;

    log.info(
        "[Integration] Received run={} team={} project={} failed={}",
        result.runId(),
        result.teamId(),
        result.projectId(),
        result.failed());

    UUID projectId = resolveProjectId(result.teamId(), result.projectId());
    if (projectId == null) {
      log.warn(
          "[Integration] Could not resolve project for team={} project={} — skipping",
          result.teamId(),
          result.projectId());
      return;
    }

    int trackersRun = runCascade(result, projectId);

    // Backward-compat: if nothing was configured via credentials, use legacy configs.
    if (trackersRun == 0) {
      runLegacy(result, projectId);
    }
  }

  /** Cascade-driven path: resolve a credential per lifecycle type and run it. */
  private int runCascade(UnifiedTestResult result, UUID projectId) {
    int run = 0;
    for (String type : trackerRegistry.lifecycleTypes()) {
      Optional<ResolvedCredential> credOpt = credentialResolver.resolve(projectId, type);
      if (credOpt.isEmpty()) continue;
      ResolvedCredential cred = credOpt.get();
      if (!cred.hasSecret()) {
        log.debug(
            "[Integration] Resolved {} for project={} but no secret — skipping", type, projectId);
        continue;
      }
      Optional<IssueTrackerPort> tracker = trackerRegistry.build(type, cred);
      if (tracker.isEmpty()) continue;
      try {
        lifecycleManager.processRun(result, projectId, cred, tracker.get());
        run++;
      } catch (Exception e) {
        log.warn(
            "[Integration] Lifecycle failed for type={} run={}: {}",
            type,
            result.runId(),
            e.getMessage());
      }
    }
    return run;
  }

  /**
   * Legacy fallback: per-team {@link IntegrationConfig} (JIRA only).
   *
   * <p>ADO-first: teams are now sub-entities of a project, so legacy configs are looked up across
   * every sub-team of the resolved project.
   */
  private void runLegacy(UnifiedTestResult result, UUID projectId) {
    List<Team> teams = teamRepo.findByProjectIdOrderByNameAsc(projectId);
    if (teams.isEmpty()) {
      log.debug("[Integration] No credentials and no sub-teams for project={}", projectId);
      return;
    }
    for (Team team : teams) {
      List<IntegrationConfig> configs = configRepo.findByTeamIdAndEnabledTrue(team.getId());
      for (IntegrationConfig config : configs) {
        try {
          IssueTrackerPort tracker = buildLegacyTracker(config);
          if (tracker == null) continue;
          lifecycleManager.processRun(result, projectId, config, tracker);
        } catch (Exception e) {
          log.warn(
              "[Integration] Legacy processing failed run={} tracker={}: {}",
              result.runId(),
              config.getTrackerType(),
              e.getMessage());
        }
      }
    }
  }

  /**
   * Resolve the project from the incoming slugs. ADO-first: {@code orgSlug} is the top-level
   * organization slug and {@code projectSlug} is unique within that org.
   */
  private UUID resolveProjectId(String orgSlug, String projectSlug) {
    Optional<Organization> org = orgRepo.findBySlug(orgSlug);
    if (org.isEmpty()) return null;
    return projectRepo
        .findByOrganizationIdAndSlug(org.get().getId(), projectSlug)
        .map(Project::getId)
        .orElse(null);
  }

  private IssueTrackerPort buildLegacyTracker(IntegrationConfig config) {
    return switch (config.getTrackerType()) {
      case "JIRA" -> {
        try {
          yield jiraFactory.create(config);
        } catch (Exception e) {
          log.warn(
              "[Integration] Could not build legacy JIRA tracker for team={}: {}",
              config.getTeamId(),
              e.getMessage());
          yield null;
        }
      }
      default -> {
        log.debug("[Integration] Unsupported legacy tracker type: {}", config.getTrackerType());
        yield null;
      }
    };
  }
}
