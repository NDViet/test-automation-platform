package com.platform.integration.port;

import com.platform.core.service.ResolvedCredential;
import com.platform.integration.azureboards.AzureBoardsIssueTracker;
import com.platform.integration.azureboards.AzureBoardsTrackerFactory;
import com.platform.integration.github.GitHubIssueTracker;
import com.platform.integration.github.GitHubIssueTrackerFactory;
import com.platform.integration.jira.JiraIssueTracker;
import com.platform.integration.jira.JiraTrackerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Builds an {@link IssueTrackerPort} for a lifecycle integration type from a
 * {@link ResolvedCredential}. Central place that knows which integration types
 * participate in the failure→ticket lifecycle.
 */
@Component
public class TrackerFactoryRegistry {

    private static final Logger log = LoggerFactory.getLogger(TrackerFactoryRegistry.class);

    /** Lifecycle (issue-tracking) integration types resolved per project. */
    public static final List<String> LIFECYCLE_TYPES =
            List.of("JIRA_CLOUD", AzureBoardsIssueTracker.TRACKER_TYPE, GitHubIssueTracker.TRACKER_TYPE);

    private final JiraTrackerFactory jiraFactory;
    private final AzureBoardsTrackerFactory azureFactory;
    private final GitHubIssueTrackerFactory githubFactory;

    public TrackerFactoryRegistry(JiraTrackerFactory jiraFactory,
                                  AzureBoardsTrackerFactory azureFactory,
                                  GitHubIssueTrackerFactory githubFactory) {
        this.jiraFactory   = jiraFactory;
        this.azureFactory  = azureFactory;
        this.githubFactory = githubFactory;
    }

    public List<String> lifecycleTypes() {
        return LIFECYCLE_TYPES;
    }

    /**
     * Builds a tracker for the given integration type from a resolved credential.
     * Returns empty if the type is unsupported or the credential is incomplete.
     */
    public Optional<IssueTrackerPort> build(String integrationType, ResolvedCredential cred) {
        try {
            return switch (integrationType) {
                case "JIRA_CLOUD", "JIRA"          -> Optional.of(jiraFactory.create(cred));
                case "AZURE_DEVOPS_BOARDS"          -> Optional.of(azureFactory.create(cred));
                case "GITHUB_ISSUES"                -> Optional.of(githubFactory.create(cred));
                default -> {
                    log.debug("[TrackerRegistry] Unsupported lifecycle type: {}", integrationType);
                    yield Optional.empty();
                }
            };
        } catch (Exception e) {
            log.warn("[TrackerRegistry] Could not build tracker for {}: {}",
                    integrationType, e.getMessage());
            return Optional.empty();
        }
    }
}
