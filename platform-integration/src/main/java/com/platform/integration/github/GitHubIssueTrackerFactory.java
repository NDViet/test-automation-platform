package com.platform.integration.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.service.ResolvedCredential;
import org.springframework.stereotype.Component;

/**
 * Builds a {@link GitHubIssueTracker} from a resolved credential.
 *
 * <p>Expected connection params: {@code owner}, {@code repo} (or a single
 * {@code repository} = "owner/repo"). Secret: {@code pat} (or {@code token}).
 * Optional {@code base_url} for GitHub Enterprise.</p>
 */
@Component
public class GitHubIssueTrackerFactory {

    private final GitHubIssueMapper mapper;
    private final ObjectMapper objectMapper;

    public GitHubIssueTrackerFactory(GitHubIssueMapper mapper, ObjectMapper objectMapper) {
        this.mapper       = mapper;
        this.objectMapper = objectMapper;
    }

    public GitHubIssueTracker create(ResolvedCredential cred) {
        String owner = cred.param("owner");
        String repo  = cred.param("repo");
        String repository = cred.param("repository");
        if ((owner == null || repo == null) && repository != null && repository.contains("/")) {
            String[] parts = repository.split("/", 2);
            owner = parts[0];
            repo  = parts[1];
        }
        String token = firstNonBlank(cred.secret("pat"), cred.secret("token"));

        if (owner == null || repo == null || token == null) {
            throw new IllegalArgumentException(
                    "GitHub Issues credential requires owner, repo and pat");
        }

        GitHubIssuesClient client = new GitHubIssuesClient(
                cred.baseUrl(), owner, repo, token, objectMapper);
        return new GitHubIssueTracker(client, mapper);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }
}
