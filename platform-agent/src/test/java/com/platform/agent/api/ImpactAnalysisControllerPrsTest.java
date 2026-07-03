package com.platform.agent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.node.tools.GitHubApiClient;
import com.platform.core.domain.ProjectIntegrationConfig;
import com.platform.core.domain.ProjectRepoAssignment;
import com.platform.core.repository.ImpactAnalysisRepository;
import com.platform.core.repository.ProjectIntegrationConfigRepository;
import com.platform.core.repository.ProjectRepoAssignmentRepository;
import com.platform.core.service.CredentialResolver;
import com.platform.core.service.ResolvedCredential;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ImpactAnalysisController#listOpenPrs} — a repo tagged Codebase must surface
 * whether it was configured via the Integrations tab ({@link ProjectIntegrationConfig}) or the
 * GitHub tab ({@link ProjectRepoAssignment}), deduped, with the assignment token resolved from the
 * credential cascade.
 */
@ExtendWith(MockitoExtension.class)
class ImpactAnalysisControllerPrsTest {

  private static final UUID PID = UUID.randomUUID();

  @Mock ImpactAnalysisRepository impactRepo;
  @Mock ProjectIntegrationConfigRepository configRepo;
  @Mock ProjectRepoAssignmentRepository assignmentRepo;
  @Mock CredentialResolver credentialResolver;
  @Mock GitHubApiClient gitHubApiClient;
  @Mock ImpactAnalysisService impactAnalysisService;

  ImpactAnalysisController controller;

  @BeforeEach
  void setUp() {
    controller =
        new ImpactAnalysisController(
            impactRepo,
            configRepo,
            assignmentRepo,
            credentialResolver,
            gitHubApiClient,
            impactAnalysisService,
            new ObjectMapper());
  }

  /** One open PR as GitHub's REST API shapes it. */
  private static String prJson(int number, String title) {
    return "[{\"number\":"
        + number
        + ",\"title\":\""
        + title
        + "\",\"html_url\":\"https://gh/pr/"
        + number
        + "\",\"state\":\"open\",\"user\":{\"login\":\"bob\"},"
        + "\"updated_at\":\"2026-07-02T00:00:00Z\","
        + "\"head\":{\"ref\":\"feature\"},\"base\":{\"ref\":\"main\"},\"body\":\"hi\"}]";
  }

  private static ProjectRepoAssignment assignment(String repoFullName, String role) {
    ProjectRepoAssignment a = mock(ProjectRepoAssignment.class);
    when(a.getRole()).thenReturn(role);
    when(a.getRepoFullName()).thenReturn(repoFullName);
    return a;
  }

  private static ResolvedCredential githubCredWithPat(String pat) {
    return new ResolvedCredential("GITHUB", null, Map.of(), Map.of("pat", pat), null);
  }

  @Test
  void listsPrsForRepoAssignedCodebaseViaGitHubTab() {
    // No Integrations-tab CODEBASE config; only a GitHub-tab assignment.
    ProjectRepoAssignment a = assignment("acme/app", "CODEBASE");
    when(configRepo.findByProjectIdAndIntegrationTypeAndRepoTypeAndEnabled(
            PID, "GITHUB", "CODEBASE", true))
        .thenReturn(List.of());
    when(assignmentRepo.findByProjectIdOrderByRepoFullName(PID)).thenReturn(List.of(a));
    when(credentialResolver.resolve(PID, "GITHUB"))
        .thenReturn(Optional.of(githubCredWithPat("tok")));
    when(gitHubApiClient.getOpenPRs("acme", "app", "tok")).thenReturn(prJson(1, "PR one"));

    List<Map<String, Object>> prs = controller.listOpenPrs(PID).getBody();

    assertThat(prs).hasSize(1);
    assertThat(prs.get(0)).containsEntry("number", 1).containsEntry("repoFullName", "acme/app");
  }

  @Test
  void listsPrsForRepoConfiguredViaIntegrationsTab_regressionGuard() {
    ProjectIntegrationConfig config = mock(ProjectIntegrationConfig.class);
    when(config.param("repoFullName")).thenReturn("acme/api");
    when(config.param("token")).thenReturn("ct");
    when(configRepo.findByProjectIdAndIntegrationTypeAndRepoTypeAndEnabled(
            PID, "GITHUB", "CODEBASE", true))
        .thenReturn(List.of(config));
    when(assignmentRepo.findByProjectIdOrderByRepoFullName(PID)).thenReturn(List.of());
    when(gitHubApiClient.getOpenPRs("acme", "api", "ct")).thenReturn(prJson(2, "PR two"));

    List<Map<String, Object>> prs = controller.listOpenPrs(PID).getBody();

    assertThat(prs).hasSize(1);
    assertThat(prs.get(0)).containsEntry("repoFullName", "acme/api");
  }

  @Test
  void dedupesRepoPresentInBothSurfaces() {
    ProjectIntegrationConfig config = mock(ProjectIntegrationConfig.class);
    when(config.param("repoFullName")).thenReturn("acme/x");
    when(config.param("token")).thenReturn("ct");
    ProjectRepoAssignment a = assignment("acme/x", "CODEBASE");
    when(configRepo.findByProjectIdAndIntegrationTypeAndRepoTypeAndEnabled(
            PID, "GITHUB", "CODEBASE", true))
        .thenReturn(List.of(config));
    when(assignmentRepo.findByProjectIdOrderByRepoFullName(PID)).thenReturn(List.of(a));
    when(gitHubApiClient.getOpenPRs("acme", "x", "ct")).thenReturn(prJson(3, "PR three"));

    List<Map<String, Object>> prs = controller.listOpenPrs(PID).getBody();

    // Fetched once (from the config surface); assignment for the same repo is skipped.
    assertThat(prs).hasSize(1);
    verify(gitHubApiClient, times(1)).getOpenPRs(eq("acme"), eq("x"), anyString());
    verify(credentialResolver, never()).resolve(any(UUID.class), anyString());
  }

  @Test
  void skipsAssignmentRepoWhenNoTokenResolves() {
    ProjectRepoAssignment a = assignment("acme/app", "CODEBASE");
    when(configRepo.findByProjectIdAndIntegrationTypeAndRepoTypeAndEnabled(
            PID, "GITHUB", "CODEBASE", true))
        .thenReturn(List.of());
    when(assignmentRepo.findByProjectIdOrderByRepoFullName(PID)).thenReturn(List.of(a));
    when(credentialResolver.resolve(PID, "GITHUB")).thenReturn(Optional.empty());

    List<Map<String, Object>> prs = controller.listOpenPrs(PID).getBody();

    assertThat(prs).isEmpty();
    verify(gitHubApiClient, never()).getOpenPRs(anyString(), anyString(), anyString());
  }
}
