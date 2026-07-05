package com.platform.ingestion.management.release;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.core.domain.SotRelease;
import com.platform.core.repository.AdoTeamRepository;
import com.platform.core.repository.SotReleaseRepository;
import com.platform.ingestion.quality.ReleaseGateService;
import com.platform.ingestion.quality.ReleaseGateService.GateCriterion;
import com.platform.ingestion.quality.ReleaseGateService.GateEvaluation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ReleaseServiceTest {

  @Mock SotReleaseRepository repo;
  @Mock AdoTeamRepository teamRepo;
  @Mock NamedParameterJdbcTemplate njdbc;
  @Mock ReleaseGateService gate;

  ReleaseService service;

  private final UUID pid = UUID.randomUUID();
  private final UUID releaseId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new ReleaseService(repo, teamRepo, njdbc, gate);
  }

  private SotRelease release(String state) {
    SotRelease r = new SotRelease(pid, "v1.0", "VERSION", null);
    r.setState(state);
    return r;
  }

  private CreateReleaseRequest toState(String state) {
    return new CreateReleaseRequest(
        "v1.0", "VERSION", null, null, state, null, null, null, null, null, null);
  }

  /** Success path reaches toDto(), which queries a run count — stub the plain JdbcTemplate. */
  private void stubToDto() {
    JdbcTemplate jt = mock(JdbcTemplate.class);
    lenient().when(njdbc.getJdbcTemplate()).thenReturn(jt);
    lenient()
        .when(
            jt.queryForObject(
                anyString(), org.mockito.ArgumentMatchers.eq(Long.class), any(), any()))
        .thenReturn(0L);
    lenient().when(repo.save(any(SotRelease.class))).thenAnswer(i -> i.getArgument(0));
  }

  @Test
  void blocksTransitionToReleasedWhenGateFails() {
    when(repo.findById(releaseId)).thenReturn(Optional.of(release("IN_PROGRESS")));
    when(gate.evaluate(eq(pid), any()))
        .thenReturn(
            new GateEvaluation(
                "FAIL", true, List.of(new GateCriterion("Pass rate", "50.0%", ">= 90.0%", false))));

    assertThatThrownBy(() -> service.update(pid, releaseId, toState("RELEASED")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Pass rate");

    verify(repo, never()).save(any());
  }

  @Test
  void allowsTransitionToReleasedWhenGatePasses() {
    stubToDto();
    when(repo.findById(releaseId)).thenReturn(Optional.of(release("IN_PROGRESS")));
    when(gate.evaluate(eq(pid), any())).thenReturn(new GateEvaluation("PASS", true, List.of()));

    service.update(pid, releaseId, toState("RELEASED"));

    verify(repo).save(any(SotRelease.class));
  }

  @Test
  void allowsTransitionWhenGateAlreadyOverridden() {
    stubToDto();
    SotRelease r = release("IN_PROGRESS");
    r.recordGateOverride("shipping hotfix", "qa-lead");
    when(repo.findById(releaseId)).thenReturn(Optional.of(r));

    service.update(pid, releaseId, toState("RELEASED"));

    verify(gate, never()).evaluate(any(), any());
    verify(repo).save(any(SotRelease.class));
  }

  @Test
  void doesNotGateNonReleasedTransitions() {
    stubToDto();
    when(repo.findById(releaseId)).thenReturn(Optional.of(release("PLANNED")));

    service.update(pid, releaseId, toState("IN_PROGRESS"));

    verify(gate, never()).evaluate(any(), any());
    verify(repo).save(any(SotRelease.class));
  }
}
