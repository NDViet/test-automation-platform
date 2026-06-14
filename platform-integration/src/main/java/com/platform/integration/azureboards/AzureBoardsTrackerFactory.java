package com.platform.integration.azureboards;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.service.ResolvedCredential;
import org.springframework.stereotype.Component;

/**
 * Builds an {@link AzureBoardsIssueTracker} from a resolved credential.
 *
 * <p>Expected connection params: {@code organization}, {@code project}
 * (or {@code project_key}), optional {@code area_path}, {@code workItemType}
 * (default Bug), {@code doneState} (default Closed), {@code reopenState}
 * (default Active). Secret: {@code pat}.</p>
 */
@Component
public class AzureBoardsTrackerFactory {

    private final AzureBoardsIssueMapper mapper;
    private final ObjectMapper objectMapper;

    public AzureBoardsTrackerFactory(AzureBoardsIssueMapper mapper, ObjectMapper objectMapper) {
        this.mapper       = mapper;
        this.objectMapper = objectMapper;
    }

    public AzureBoardsIssueTracker create(ResolvedCredential cred) {
        String organization = firstNonBlank(cred.param("organization"), cred.param("org"));
        String project      = firstNonBlank(cred.param("project"), cred.param("project_key"));
        String pat          = firstNonBlank(cred.secret("pat"), cred.secret("token"));

        if (organization == null || project == null || pat == null) {
            throw new IllegalArgumentException(
                    "Azure Boards credential requires organization, project and pat");
        }

        AzureBoardsClient client = new AzureBoardsClient(
                cred.baseUrl(), organization, project, pat, objectMapper);

        return new AzureBoardsIssueTracker(client, mapper,
                cred.param("workItemType"),
                cred.param("area_path"),
                firstNonBlank(cred.param("doneState"), "Closed"),
                firstNonBlank(cred.param("reopenState"), "Active"));
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }
}
