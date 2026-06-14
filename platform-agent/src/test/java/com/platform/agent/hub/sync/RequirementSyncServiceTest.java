package com.platform.agent.hub.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.PlatformRequirement;
import com.platform.core.domain.ProjectIntegrationConfig;
import com.platform.core.mapping.MappingProfileApplier;
import com.platform.core.mapping.MappingRules;
import com.platform.core.mapping.MappingRulesProvider;
import com.platform.core.repository.PlatformRequirementRepository;
import com.platform.core.repository.ProjectIntegrationConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RequirementSyncServiceTest {

    private PlatformRequirementRepository requirementRepo;
    private ProjectIntegrationConfigRepository configRepo;
    private RequirementSyncService service;

    private final UUID projectId = UUID.randomUUID();
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() {
        requirementRepo = mock(PlatformRequirementRepository.class);
        configRepo = mock(ProjectIntegrationConfigRepository.class);
        MappingRulesProvider rulesProvider = mock(MappingRulesProvider.class);
        MappingRules defaults = MappingRulesProvider.loadDefault(om);
        when(rulesProvider.effectiveForProject(any())).thenReturn(defaults);

        service = new RequirementSyncService(requirementRepo, configRepo,
                rulesProvider, new MappingProfileApplier(), om);

        when(requirementRepo.findByProjectIdAndExternalId(any(), any())).thenReturn(Optional.empty());
        when(requirementRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private ProjectIntegrationConfig config(String type, Map<String, String> params) {
        ProjectIntegrationConfig c = new ProjectIntegrationConfig(
                projectId, "REQUIREMENT", type, "disp", "INBOUND");
        c.setConnectionParams(params);
        return c;
    }

    private PlatformRequirement captureSaved() {
        ArgumentCaptor<PlatformRequirement> cap = ArgumentCaptor.forClass(PlatformRequirement.class);
        verify(requirementRepo).save(cap.capture());
        return cap.getValue();
    }

    @Test
    void azureBoards_appliesProfileAndStoresRawPayload() {
        when(configRepo.findByIntegrationTypeAndEnabled("AZURE_DEVOPS_BOARDS", true))
                .thenReturn(List.of(config("AZURE_DEVOPS_BOARDS", Map.of("project", "MyProject"))));

        String payload = """
            {"eventType":"workitem.created","resource":{"id":42,"fields":{
              "System.Title":"Login fails","System.Description":"repro steps",
              "System.WorkItemType":"Bug","System.TeamProject":"MyProject",
              "System.State":"Active","Microsoft.VSTS.Common.Priority":"2",
              "Acme.JiraId":"JIRA-9"}}}
            """;

        Optional<UUID> result = service.syncFromAzureBoards(payload);

        assertThat(result).contains(projectId);
        PlatformRequirement r = captureSaved();
        assertThat(r.getExternalId()).isEqualTo("42");
        assertThat(r.getTitle()).isEqualTo("Login fails");
        assertThat(r.getIssueType()).isEqualTo("DEFECT");
        assertThat(r.getStatus()).isEqualTo("IN_PROGRESS");   // Active → InProgress → IN_PROGRESS
        assertThat(r.getPriority()).isEqualTo("P2");          // "2" → P2
        // raw payload preserved (incl. unmapped/custom fields) for drift safety
        assertThat(r.getRawUpstream()).containsEntry("Acme.JiraId", "JIRA-9");
    }

    @Test
    void azureBoards_noMatchingProject_returnsEmpty() {
        when(configRepo.findByIntegrationTypeAndEnabled("AZURE_DEVOPS_BOARDS", true))
                .thenReturn(List.of(config("AZURE_DEVOPS_BOARDS", Map.of("project", "Other"))));

        String payload = """
            {"eventType":"workitem.created","resource":{"id":42,"fields":{
              "System.Title":"x","System.TeamProject":"MyProject"}}}
            """;

        assertThat(service.syncFromAzureBoards(payload)).isEmpty();
        verify(requirementRepo, never()).save(any());
    }

    @Test
    void existingRequirement_mergeIsNonDestructive() {
        when(configRepo.findByIntegrationTypeAndEnabled("AZURE_DEVOPS_BOARDS", true))
                .thenReturn(List.of(config("AZURE_DEVOPS_BOARDS", Map.of("project", "MyProject"))));

        PlatformRequirement existing = new PlatformRequirement(
                projectId, UUID.randomUUID(), "42", "Old title", "Old desc", "DEFECT");
        when(requirementRepo.findByProjectIdAndExternalId(projectId, "42"))
                .thenReturn(Optional.of(existing));

        // Upstream now omits the description (e.g. field removed/blanked) — must NOT clobber it.
        String payload = """
            {"resource":{"id":42,"fields":{
              "System.Title":"New title","System.Description":"",
              "System.WorkItemType":"Bug","System.TeamProject":"MyProject"}}}
            """;

        service.syncFromAzureBoards(payload);

        PlatformRequirement r = captureSaved();
        assertThat(r.getTitle()).isEqualTo("New title");   // present → updated
        assertThat(r.getDescription()).isEqualTo("Old desc"); // blank upstream → kept (non-destructive)
    }

    @Test
    void gitHubIssues_upsertsIssueWithRepoScopedExternalId() {
        when(configRepo.findByIntegrationTypeAndEnabled("GITHUB_ISSUES", true))
                .thenReturn(List.of(config("GITHUB_ISSUES", Map.of("repository", "acme/checkout"))));

        String payload = """
            {"action":"opened","issue":{"number":7,"title":"Cart bug","body":"oops",
              "labels":[{"name":"bug"}]},"repository":{"full_name":"acme/checkout"}}
            """;

        Optional<UUID> result = service.syncFromGitHubIssues(payload);

        assertThat(result).contains(projectId);
        PlatformRequirement r = captureSaved();
        assertThat(r.getExternalId()).isEqualTo("acme/checkout#7");
        assertThat(r.getTitle()).isEqualTo("Cart bug");
        assertThat(r.getIssueType()).isEqualTo("DEFECT");
    }

    @Test
    void gitHubIssues_matchesByOwnerRepoParams() {
        when(configRepo.findByIntegrationTypeAndEnabled("GITHUB_ISSUES", true))
                .thenReturn(List.of(config("GITHUB_ISSUES", Map.of("owner", "acme", "repo", "checkout"))));

        String payload = """
            {"action":"opened","issue":{"number":3,"title":"t","body":"b","labels":[]},
             "repository":{"full_name":"acme/checkout"}}
            """;

        assertThat(service.syncFromGitHubIssues(payload)).contains(projectId);
        PlatformRequirement r = captureSaved();
        assertThat(r.getExternalId()).isEqualTo("acme/checkout#3");
        assertThat(r.getIssueType()).isEqualTo("STORY");
    }
}
