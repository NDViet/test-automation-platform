package com.platform.agent.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.agent.agents.TaskAgentDtos.EffectiveAssignmentDto;
import com.platform.agent.agents.TaskAgentDtos.TaskAgentDto;
import com.platform.agent.agents.TaskAgentDtos.TaskAgentRequest;
import com.platform.agent.agents.TaskAgentDtos.TaskSubTypeDto;
import com.platform.common.agent.AgentTaskType;
import com.platform.core.domain.Agent;
import com.platform.core.domain.Organization;
import com.platform.core.domain.Project;
import com.platform.core.domain.TaskAgentAssignment;
import com.platform.core.repository.AgentRepository;
import com.platform.core.repository.ProjectRepository;
import com.platform.core.repository.TaskAgentAssignmentRepository;
import com.platform.core.repository.TaskSubTypeRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TaskAgentServiceTest {

  @Mock TaskAgentAssignmentRepository repo;
  @Mock TaskSubTypeRepository subTypeRepo;
  @Mock AgentRepository agentRepo;
  @Mock ProjectRepository projectRepo;
  @Mock AgentResolutionService resolutionService;
  @Mock AgentRbacGuard rbacGuard;

  TaskAgentService service;

  private final UUID projectId = UUID.randomUUID();
  private final UUID orgId = UUID.randomUUID();
  private static final String TASK = "GENERATE_TEST_CASES";

  @BeforeEach
  void setUp() {
    service =
        new TaskAgentService(
            repo, subTypeRepo, agentRepo, projectRepo, resolutionService, rbacGuard);
    Project project = mock(Project.class);
    Organization organization = mock(Organization.class);
    lenient().when(organization.getId()).thenReturn(orgId);
    lenient().when(project.getOrganization()).thenReturn(organization);
    lenient().when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));
  }

  private Agent projectAgent() {
    return new Agent(
        "PROJECT", projectId, "Func", "d", null, null, null, null, "COMPLEX", null, null, 3, true,
        "u");
  }

  @Test
  void upsertCreatesAssignmentWhenAbsent() {
    UUID agentId = UUID.randomUUID();
    when(agentRepo.findById(agentId)).thenReturn(Optional.of(projectAgent()));
    when(repo.findByScopeAndScopeIdAndTaskTypeAndSubType(
            "PROJECT", projectId, TASK, "NON_FUNCTIONAL"))
        .thenReturn(Optional.empty());
    when(repo.save(any(TaskAgentAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

    TaskAgentDto dto =
        service.upsert(
            "PROJECT", projectId, new TaskAgentRequest(TASK, "NON_FUNCTIONAL", agentId), "u");

    assertThat(dto.taskType()).isEqualTo(TASK);
    assertThat(dto.subType()).isEqualTo("NON_FUNCTIONAL");
    assertThat(dto.agentId()).isEqualTo(agentId);
  }

  @Test
  void upsertRejectsInvisibleAgent() {
    UUID agentId = UUID.randomUUID();
    Agent foreign =
        new Agent(
            "PROJECT",
            UUID.randomUUID(),
            "X",
            "d",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            3,
            true,
            "u");
    when(agentRepo.findById(agentId)).thenReturn(Optional.of(foreign));

    assertThatThrownBy(
            () ->
                service.upsert(
                    "PROJECT", projectId, new TaskAgentRequest(TASK, "DEFAULT", agentId), "u"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("not visible");
  }

  @Test
  void subTypesReturnsImplicitDefaultWhenCatalogEmpty() {
    when(subTypeRepo.findByTaskTypeOrderByKeyAsc(TASK)).thenReturn(List.of());

    List<TaskSubTypeDto> subs = service.subTypes(TASK);

    assertThat(subs).hasSize(1);
    assertThat(subs.get(0).key()).isEqualTo("DEFAULT");
    assertThat(subs.get(0).isDefault()).isTrue();
  }

  @Test
  void effectiveDelegatesToResolution() {
    when(resolutionService.resolve(
            eq(projectId), eq(AgentTaskType.GENERATE_TEST_CASES), isNull(), isNull()))
        .thenReturn(
            new EffectiveAgentConfig(
                EffectiveAgentConfig.Source.SEED,
                null,
                "sys",
                "usr",
                null,
                "COMPLEX",
                List.of(),
                java.util.Map.of(),
                3));

    EffectiveAssignmentDto dto = service.effective(projectId, TASK, null);

    assertThat(dto.source()).isEqualTo("SEED");
    assertThat(dto.agentId()).isNull();
    assertThat(dto.agentName()).contains("seed");
  }

  @Test
  void deleteOwnedRemoves() {
    UUID id = UUID.randomUUID();
    TaskAgentAssignment a =
        new TaskAgentAssignment(
            "PROJECT", projectId, TASK, "DEFAULT", UUID.randomUUID(), true, "u");
    when(repo.findById(id)).thenReturn(Optional.of(a));

    service.delete("PROJECT", projectId, id, "u");

    verify(repo).delete(a);
  }

  @Test
  void upsertDeniedByRbac() {
    org.mockito.Mockito.doThrow(
            new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "nope"))
        .when(rbacGuard)
        .requireManage("PROJECT", projectId);

    assertThatThrownBy(
            () ->
                service.upsert(
                    "PROJECT",
                    projectId,
                    new TaskAgentRequest(TASK, "DEFAULT", UUID.randomUUID()),
                    "u"))
        .isInstanceOf(ResponseStatusException.class);
  }
}
