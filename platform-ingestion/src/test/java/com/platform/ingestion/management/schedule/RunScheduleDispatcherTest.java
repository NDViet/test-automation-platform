package com.platform.ingestion.management.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.RunSchedule;
import com.platform.core.repository.RunScheduleRepository;
import com.platform.ingestion.management.ProjectGitHubWorkflowService;
import com.platform.ingestion.management.ProjectGitHubWorkflowService.DispatchRequest;
import com.platform.ingestion.management.ProjectGitHubWorkflowService.DispatchResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RunScheduleDispatcherTest {

  @Mock RunScheduleRepository repo;
  @Mock ProjectGitHubWorkflowService github;

  RunScheduleDispatcher dispatcher;

  @BeforeEach
  void setUp() {
    dispatcher = new RunScheduleDispatcher(repo, github, new ObjectMapper());
  }

  private RunSchedule schedule() {
    return new RunSchedule(
        UUID.randomUUID(),
        "Nightly",
        "acme/app",
        42L,
        "main",
        "{\"suite\":\"smoke\"}",
        60,
        true,
        Instant.parse("2026-07-04T00:00:00Z"));
  }

  @Test
  void pollDispatchesDueSchedulesAndAdvancesNextRun() {
    RunSchedule s = schedule();
    when(repo.findDueEnabled(any())).thenReturn(List.of(s));
    when(github.triggerDispatch(eq(s.getProjectId()), any()))
        .thenReturn(new DispatchResult(true, "queued"));

    dispatcher.pollAndDispatch();

    ArgumentCaptor<DispatchRequest> req = ArgumentCaptor.forClass(DispatchRequest.class);
    verify(github).triggerDispatch(eq(s.getProjectId()), req.capture());
    assertThat(req.getValue().repoFullName()).isEqualTo("acme/app");
    assertThat(req.getValue().workflowId()).isEqualTo(42L);
    assertThat(req.getValue().inputs()).containsEntry("suite", "smoke");
    assertThat(s.getLastDispatchedAt()).isNotNull();
    assertThat(s.getNextRunAt()).isAfter(Instant.parse("2026-07-04T00:00:00Z"));
    verify(repo).save(s);
  }

  @Test
  void dispatchFailureStillAdvancesNextRun() {
    RunSchedule s = schedule();
    when(github.triggerDispatch(any(), any())).thenThrow(new RuntimeException("502 bad gateway"));

    DispatchResult result = dispatcher.dispatch(s);

    assertThat(result.triggered()).isFalse();
    assertThat(s.getLastDispatchedAt()).isNotNull();
    verify(repo).save(s);
  }
}
