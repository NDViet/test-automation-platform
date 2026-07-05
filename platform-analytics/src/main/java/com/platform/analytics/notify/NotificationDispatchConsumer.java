package com.platform.analytics.notify;

import com.platform.common.dto.UnifiedTestResult;
import com.platform.common.kafka.Topics;
import com.platform.core.domain.Organization;
import com.platform.core.domain.Project;
import com.platform.core.repository.OrganizationRepository;
import com.platform.core.repository.ProjectRepository;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Turns ingested run results into notifications: fires RUN_COMPLETED for every result and
 * RUN_HAS_FAILURES when the run had failures/broken tests, dispatched to the project's subscribed
 * routes. Runs in its own consumer group so it doesn't interfere with the analytics scoring
 * consumer.
 *
 * <p>Review-requested and gate-result events live on other buses/modules and are documented
 * follow-ups.
 */
@Component
public class NotificationDispatchConsumer {

  private static final Logger log = LoggerFactory.getLogger(NotificationDispatchConsumer.class);

  private final NotificationDispatchService dispatch;
  private final OrganizationRepository orgRepo;
  private final ProjectRepository projectRepo;

  public NotificationDispatchConsumer(
      NotificationDispatchService dispatch,
      OrganizationRepository orgRepo,
      ProjectRepository projectRepo) {
    this.dispatch = dispatch;
    this.orgRepo = orgRepo;
    this.projectRepo = projectRepo;
  }

  @KafkaListener(
      topics = Topics.TEST_RESULTS_RAW,
      groupId = "${spring.kafka.consumer.group-id:platform-analytics}-notify",
      containerFactory = "kafkaListenerContainerFactory")
  public void onResult(UnifiedTestResult result) {
    UUID projectId = resolveProjectId(result.teamId(), result.projectId());
    if (projectId == null) {
      return;
    }
    String scope = result.projectId() + " · " + result.branch();
    String body =
        String.format(
            "%d/%d passed, %d failed, %d broken (%s)",
            result.passed(), result.total(), result.failed(), result.broken(), scope);

    dispatch.dispatch(projectId, "RUN_COMPLETED", "Run completed", body);
    if (result.failed() + result.broken() > 0) {
      dispatch.dispatch(projectId, "RUN_HAS_FAILURES", "Run has failures", body);
    }
    log.debug(
        "[Notify] dispatched run notifications for project={} run={}", projectId, result.runId());
  }

  private UUID resolveProjectId(String orgSlug, String projectSlug) {
    Optional<Organization> org = orgRepo.findBySlug(orgSlug);
    if (org.isEmpty()) {
      return null;
    }
    return projectRepo
        .findByOrganizationIdAndSlug(org.get().getId(), projectSlug)
        .map(Project::getId)
        .orElse(null);
  }
}
