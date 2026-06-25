package com.platform.integration.jira;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.IntegrationConfig;
import com.platform.core.service.ResolvedCredential;
import org.springframework.stereotype.Component;

/** Creates a {@link JiraIssueTracker} from a persisted {@link IntegrationConfig}. */
@Component
public class JiraTrackerFactory {

  private final JiraIssueMapper mapper;
  private final ObjectMapper objectMapper;

  public JiraTrackerFactory(JiraIssueMapper mapper, ObjectMapper objectMapper) {
    this.mapper = mapper;
    this.objectMapper = objectMapper;
  }

  public JiraIssueTracker create(IntegrationConfig config) {
    String email = config.config("email");
    String apiToken = config.config("apiToken");
    String done = config.config("doneTransitionName", "Done");
    String reopen = config.config("reopenTransitionName", "Reopen");

    if (email == null || apiToken == null) {
      throw new IllegalArgumentException(
          "JIRA config for team " + config.getTeamId() + " is missing email or apiToken");
    }

    JiraClient client = new JiraClient(config.getBaseUrl(), email, apiToken, objectMapper);
    return new JiraIssueTracker(
        client, mapper, config.getProjectKey(), done, reopen, config.getBaseUrl());
  }

  /**
   * Creates a {@link JiraIssueTracker} from a {@link ResolvedCredential} (the Org→Team→Project
   * cascade). {@code email}/{@code project_key}/transition names are non-secret connection params;
   * {@code apiToken} is the decrypted secret.
   */
  public JiraIssueTracker create(ResolvedCredential cred) {
    String email = cred.param("email");
    String apiToken = firstNonBlank(cred.secret("apiToken"), cred.secret("api_token"));
    String projectKey = firstNonBlank(cred.param("project_key"), cred.param("projectKey"));
    String done = firstNonBlank(cred.param("doneTransitionName"), "Done");
    String reopen = firstNonBlank(cred.param("reopenTransitionName"), "Reopen");

    if (email == null || apiToken == null) {
      throw new IllegalArgumentException("Resolved JIRA credential is missing email or apiToken");
    }

    JiraClient client = new JiraClient(cred.baseUrl(), email, apiToken, objectMapper);
    return new JiraIssueTracker(client, mapper, projectKey, done, reopen, cred.baseUrl());
  }

  private static String firstNonBlank(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) return v;
    }
    return null;
  }
}
