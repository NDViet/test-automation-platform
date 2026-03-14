package com.platform.integration.jira;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.IntegrationConfig;
import org.springframework.stereotype.Component;

/**
 * Creates a {@link JiraIssueTracker} from a persisted {@link IntegrationConfig}.
 */
@Component
public class JiraTrackerFactory {

    private final JiraIssueMapper mapper;
    private final ObjectMapper objectMapper;

    public JiraTrackerFactory(JiraIssueMapper mapper, ObjectMapper objectMapper) {
        this.mapper       = mapper;
        this.objectMapper = objectMapper;
    }

    public JiraIssueTracker create(IntegrationConfig config) {
        String email     = config.config("email");
        String apiToken  = config.config("apiToken");
        String done      = config.config("doneTransitionName", "Done");
        String reopen    = config.config("reopenTransitionName", "Reopen");

        if (email == null || apiToken == null) {
            throw new IllegalArgumentException(
                    "JIRA config for team " + config.getTeamId() + " is missing email or apiToken");
        }

        JiraClient client = new JiraClient(config.getBaseUrl(), email, apiToken, objectMapper);
        return new JiraIssueTracker(client, mapper, config.getProjectKey(),
                done, reopen, config.getBaseUrl());
    }
}
