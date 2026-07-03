package com.platform.agent.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.node.tools.GitHubApiClient;
import com.platform.core.domain.ImpactAnalysis;
import com.platform.core.repository.ImpactAnalysisRepository;
import com.platform.core.repository.ProjectIntegrationConfigRepository;
import com.platform.core.repository.ProjectRepoAssignmentRepository;
import com.platform.core.service.CredentialResolver;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Update + re-run behaviour of {@link ImpactAnalysisController#update}. */
@ExtendWith(MockitoExtension.class)
class ImpactAnalysisControllerUpdateTest {

  private static final UUID PID = UUID.randomUUID();
  private static final UUID ID = UUID.randomUUID();

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

  private static ImpactAnalysisController.CreateImpactAnalysisRequest req() {
    return new ImpactAnalysisController.CreateImpactAnalysisRequest(
        "Updated",
        List.of(
            new ImpactAnalysisController.LinkedPrRef(
                "acme/app", 7, "https://gh/pr/7", "PR 7", "PR body text")),
        List.of("req-1"),
        "focus on payments");
  }

  @Test
  void updateReplacesFieldsRerunsAndReturns200() {
    ImpactAnalysis existing = new ImpactAnalysis();
    existing.setProjectId(PID);
    existing.setStatus("COMPLETED");
    existing.setSummary("old summary");
    when(impactRepo.findById(ID)).thenReturn(Optional.of(existing));
    when(impactRepo.save(any(ImpactAnalysis.class))).thenAnswer(inv -> inv.getArgument(0));

    ResponseEntity<ImpactAnalysis> resp = controller.update(PID, ID, req());

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    ImpactAnalysis body = resp.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getName()).isEqualTo("Updated");
    assertThat(body.getStatus()).isEqualTo("RUNNING");
    assertThat(body.getCustomPrompt()).isEqualTo("focus on payments");
    assertThat(body.getLinkedRequirementIds()).containsExactly("req-1");
    assertThat(body.getLinkedPrs()).hasSize(1);
    assertThat(body.getSummary()).isNull(); // prior result cleared for a fresh run
    verify(impactAnalysisService, times(1)).runAnalysis(any(), eq(PID));
  }

  @Test
  void updateReturns409WhenAlreadyRunning() {
    ImpactAnalysis running = new ImpactAnalysis();
    running.setProjectId(PID);
    running.setStatus("RUNNING");
    when(impactRepo.findById(ID)).thenReturn(Optional.of(running));

    ResponseEntity<ImpactAnalysis> resp = controller.update(PID, ID, req());

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    verify(impactAnalysisService, never()).runAnalysis(any(), any());
    verify(impactRepo, never()).save(any());
  }

  @Test
  void updateReturns404OnProjectMismatch() {
    ImpactAnalysis other = new ImpactAnalysis();
    other.setProjectId(UUID.randomUUID());
    when(impactRepo.findById(ID)).thenReturn(Optional.of(other));

    ResponseEntity<ImpactAnalysis> resp = controller.update(PID, ID, req());

    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    verify(impactAnalysisService, never()).runAnalysis(any(), any());
  }
}
