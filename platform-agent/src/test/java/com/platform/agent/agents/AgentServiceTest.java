package com.platform.agent.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.Agent;
import com.platform.core.domain.AiPromptTemplate;
import com.platform.core.domain.Organization;
import com.platform.core.domain.Project;
import com.platform.core.repository.AgentRepository;
import com.platform.core.repository.AiPromptTemplateRepository;
import com.platform.core.repository.AiSkillRepository;
import com.platform.core.repository.ProjectRepository;
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
class AgentServiceTest {

  @Mock AgentRepository repo;
  @Mock AiPromptTemplateRepository templateRepo;
  @Mock AiSkillRepository skillRepo;
  @Mock ProjectRepository projectRepo;
  @Mock AgentRbacGuard rbacGuard;

  AgentService service;

  private final UUID projectId = UUID.randomUUID();
  private final UUID orgId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new AgentService(repo, templateRepo, skillRepo, projectRepo, rbacGuard, new ObjectMapper());
    Project project = mock(Project.class);
    Organization organization = mock(Organization.class);
    lenient().when(organization.getId()).thenReturn(orgId);
    lenient().when(project.getOrganization()).thenReturn(organization);
    lenient().when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));
  }

  private AgentRequest req(String name) {
    return new AgentRequest(
        name, "d", "persona", null, null, List.of(), "COMPLEX", null, null, 3, true);
  }

  @Test
  void createProjectAgentPersists() {
    when(repo.findByScopeAndScopeIdAndName("PROJECT", projectId, "Func"))
        .thenReturn(Optional.empty());
    when(repo.save(any(Agent.class))).thenAnswer(inv -> inv.getArgument(0));

    AgentDto dto = service.create("PROJECT", projectId, req("Func"), "alice");

    assertThat(dto.scope()).isEqualTo("PROJECT");
    assertThat(dto.scopeId()).isEqualTo(projectId);
    assertThat(dto.name()).isEqualTo("Func");
    assertThat(dto.modelRole()).isEqualTo("COMPLEX");
  }

  @Test
  void duplicateNameConflicts() {
    when(repo.findByScopeAndScopeIdAndName("PROJECT", projectId, "Func"))
        .thenReturn(Optional.of(mock(Agent.class)));

    assertThatThrownBy(() -> service.create("PROJECT", projectId, req("Func"), "alice"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("already exists");
  }

  @Test
  void outOfScopeTemplateRejected() {
    UUID templateId = UUID.randomUUID();
    UUID otherProject = UUID.randomUUID();
    AiPromptTemplate foreign =
        new AiPromptTemplate(otherProject, "SYSTEM", "T", "body", false, "u");
    when(templateRepo.findById(templateId)).thenReturn(Optional.of(foreign));
    AgentRequest r =
        new AgentRequest("X", null, null, templateId, null, List.of(), null, null, null, 3, true);

    assertThatThrownBy(() -> service.create("PROJECT", projectId, r, "alice"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("not visible");
  }

  @Test
  void effectiveMergesProjectOverOrgByName() {
    Agent orgDefault =
        new Agent(
            "ORG",
            orgId,
            "Default",
            "d",
            null,
            null,
            null,
            null,
            "STANDARD",
            null,
            null,
            3,
            true,
            "u");
    Agent orgOnly =
        new Agent(
            "ORG",
            orgId,
            "OrgOnly",
            "d",
            null,
            null,
            null,
            null,
            "STANDARD",
            null,
            null,
            3,
            true,
            "u");
    Agent projectDefault =
        new Agent(
            "PROJECT", projectId, "Default", "d", null, null, null, null, "COMPLEX", null, null, 3,
            true, "u");
    when(repo.findByScopeAndScopeIdOrderByNameAsc("ORG", orgId))
        .thenReturn(List.of(orgDefault, orgOnly));
    when(repo.findByScopeAndScopeIdOrderByNameAsc("PROJECT", projectId))
        .thenReturn(List.of(projectDefault));

    List<AgentDto> eff = service.effectiveForProject(projectId);

    assertThat(eff).hasSize(2);
    AgentDto def = eff.stream().filter(a -> a.name().equals("Default")).findFirst().orElseThrow();
    assertThat(def.inherited()).isFalse(); // project shadows org
    assertThat(def.modelRole()).isEqualTo("COMPLEX");
    AgentDto only = eff.stream().filter(a -> a.name().equals("OrgOnly")).findFirst().orElseThrow();
    assertThat(only.inherited()).isTrue();
  }

  @Test
  void deleteOwnedRemoves() {
    UUID id = UUID.randomUUID();
    Agent agent =
        new Agent(
            "PROJECT", projectId, "X", "d", null, null, null, null, null, null, null, 3, true, "u");
    when(repo.findById(id)).thenReturn(Optional.of(agent));

    service.delete("PROJECT", projectId, id, "alice");

    verify(repo).delete(agent);
  }

  @Test
  void createDeniedByRbac() {
    org.mockito.Mockito.doThrow(
            new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "nope"))
        .when(rbacGuard)
        .requireManage("PROJECT", projectId);

    assertThatThrownBy(() -> service.create("PROJECT", projectId, req("X"), "alice"))
        .isInstanceOf(ResponseStatusException.class);
    verify(repo, org.mockito.Mockito.never()).save(any());
  }
}
