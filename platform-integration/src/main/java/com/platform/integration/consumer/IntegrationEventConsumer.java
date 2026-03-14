package com.platform.integration.consumer;

import com.platform.common.dto.UnifiedTestResult;
import com.platform.common.kafka.Topics;
import com.platform.core.domain.IntegrationConfig;
import com.platform.core.domain.Project;
import com.platform.core.domain.Team;
import com.platform.core.repository.IntegrationConfigRepository;
import com.platform.core.repository.ProjectRepository;
import com.platform.core.repository.TeamRepository;
import com.platform.integration.jira.JiraTrackerFactory;
import com.platform.integration.lifecycle.TicketLifecycleManager;
import com.platform.integration.port.IssueTrackerPort;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consumes {@link UnifiedTestResult} events and drives the ticket lifecycle
 * for each configured issue tracker integration.
 */
@Component
public class IntegrationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(IntegrationEventConsumer.class);

    private final TeamRepository teamRepo;
    private final ProjectRepository projectRepo;
    private final IntegrationConfigRepository configRepo;
    private final TicketLifecycleManager lifecycleManager;
    private final JiraTrackerFactory jiraFactory;

    public IntegrationEventConsumer(TeamRepository teamRepo,
                                     ProjectRepository projectRepo,
                                     IntegrationConfigRepository configRepo,
                                     TicketLifecycleManager lifecycleManager,
                                     JiraTrackerFactory jiraFactory) {
        this.teamRepo         = teamRepo;
        this.projectRepo      = projectRepo;
        this.configRepo       = configRepo;
        this.lifecycleManager = lifecycleManager;
        this.jiraFactory      = jiraFactory;
    }

    @KafkaListener(
            topics = Topics.TEST_RESULTS_RAW,
            groupId = "${spring.kafka.consumer.group-id:platform-integration}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, UnifiedTestResult> record) {
        UnifiedTestResult result = record.value();
        if (result == null) return;

        log.info("[Integration] Received run={} team={} project={} failed={}",
                result.runId(), result.teamId(), result.projectId(), result.failed());

        UUID projectId = resolveProjectId(result.teamId(), result.projectId());
        if (projectId == null) {
            log.warn("[Integration] Could not resolve project for team={} project={} — skipping",
                    result.teamId(), result.projectId());
            return;
        }

        UUID teamId = resolveTeamId(result.teamId());
        if (teamId == null) return;

        List<IntegrationConfig> configs = configRepo.findByTeamIdAndEnabledTrue(teamId);
        if (configs.isEmpty()) {
            log.debug("[Integration] No enabled integrations for team={}", result.teamId());
            return;
        }

        for (IntegrationConfig config : configs) {
            try {
                IssueTrackerPort tracker = buildTracker(config);
                if (tracker == null) continue;
                lifecycleManager.processRun(result, projectId, config, tracker);
            } catch (Exception e) {
                log.warn("[Integration] Failed to process run={} for tracker={}: {}",
                        result.runId(), config.getTrackerType(), e.getMessage());
            }
        }
    }

    private UUID resolveProjectId(String teamSlug, String projectSlug) {
        Optional<Team> team = teamRepo.findBySlug(teamSlug);
        if (team.isEmpty()) return null;
        return projectRepo.findByTeamIdAndSlug(team.get().getId(), projectSlug)
                .map(Project::getId)
                .orElse(null);
    }

    private UUID resolveTeamId(String teamSlug) {
        return teamRepo.findBySlug(teamSlug).map(Team::getId).orElse(null);
    }

    private IssueTrackerPort buildTracker(IntegrationConfig config) {
        return switch (config.getTrackerType()) {
            case "JIRA" -> {
                try { yield jiraFactory.create(config); }
                catch (Exception e) {
                    log.warn("[Integration] Could not build JIRA tracker for team={}: {}",
                            config.getTeamId(), e.getMessage());
                    yield null;
                }
            }
            default -> {
                log.debug("[Integration] Unsupported tracker type: {}", config.getTrackerType());
                yield null;
            }
        };
    }
}
