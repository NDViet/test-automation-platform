package com.platform.agent.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.agent.AgentTaskType;
import com.platform.core.domain.Agent;
import com.platform.core.domain.AiPromptTemplate;
import com.platform.core.domain.AiSkill;
import com.platform.core.domain.Organization;
import com.platform.core.domain.Project;
import com.platform.core.domain.TaskAgentAssignment;
import com.platform.core.domain.TaskSubType;
import com.platform.core.repository.AgentRepository;
import com.platform.core.repository.AiPromptTemplateRepository;
import com.platform.core.repository.AiSkillRepository;
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
class AgentResolutionServiceTest {

  @Mock AgentRepository agentRepo;
  @Mock TaskAgentAssignmentRepository assignmentRepo;
  @Mock TaskSubTypeRepository subTypeRepo;
  @Mock AiPromptTemplateRepository templateRepo;
  @Mock AiSkillRepository skillRepo;
  @Mock ProjectRepository projectRepo;

  AgentResolutionService service;

  private final UUID projectId = UUID.randomUUID();
  private final UUID orgId = UUID.randomUUID();
  private static final AgentTaskType TASK = AgentTaskType.GENERATE_TEST_CASES;

  @BeforeEach
  void setUp() {
    service =
        new AgentResolutionService(
            agentRepo,
            assignmentRepo,
            subTypeRepo,
            templateRepo,
            skillRepo,
            projectRepo,
            new SeedAgentCatalog(),
            new com.platform.agent.api.AiPromptTemplateService(templateRepo),
            new ObjectMapper());

    // No project default templates ⇒ resolveDefault falls back to the built-in seed prompts.
    lenient()
        .when(templateRepo.findByProjectIdAndKindAndIsDefaultTrue(any(), anyString()))
        .thenReturn(Optional.empty());

    Project project = mock(Project.class);
    Organization organization = mock(Organization.class);
    lenient().when(organization.getId()).thenReturn(orgId);
    lenient().when(project.getOrganization()).thenReturn(organization);
    lenient().when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));
  }

  private Agent projectAgent(String name, String persona, String skillIdsJson, String modelId) {
    return new Agent(
        "PROJECT",
        projectId,
        name,
        "d",
        persona,
        null,
        null,
        skillIdsJson,
        "COMPLEX",
        modelId,
        null,
        2,
        true,
        "u");
  }

  // ── cascade ────────────────────────────────────────────────────────────────

  @Test
  void seedWhenNothingConfigured() {
    when(assignmentRepo.findByScopeAndScopeIdAndTaskTypeAndSubType(
            "PROJECT", projectId, TASK.name(), "DEFAULT"))
        .thenReturn(Optional.empty());
    when(assignmentRepo.findByScopeAndScopeIdAndTaskTypeAndSubType(
            "ORG", orgId, TASK.name(), "DEFAULT"))
        .thenReturn(Optional.empty());

    EffectiveAgentConfig cfg = service.resolve(projectId, TASK, "DEFAULT", null);

    assertThat(cfg.source()).isEqualTo(EffectiveAgentConfig.Source.SEED);
    assertThat(cfg.agentId()).isNull();
    assertThat(cfg.modelRole()).isEqualTo("COMPLEX");
    assertThat(cfg.maxRounds()).isEqualTo(0);
    assertThat(cfg.systemPrompt()).contains("manual test cases");
  }

  @Test
  void projectAssignmentResolvesFirst() {
    UUID agentId = UUID.randomUUID();
    when(assignmentRepo.findByScopeAndScopeIdAndTaskTypeAndSubType(
            "PROJECT", projectId, TASK.name(), "DEFAULT"))
        .thenReturn(
            Optional.of(
                new TaskAgentAssignment(
                    "PROJECT", projectId, TASK.name(), "DEFAULT", agentId, true, "u")));
    when(agentRepo.findById(agentId))
        .thenReturn(Optional.of(projectAgent("Func", "You are functional.", null, "test-model-x")));

    EffectiveAgentConfig cfg = service.resolve(projectId, TASK, "DEFAULT", null);

    assertThat(cfg.source()).isEqualTo(EffectiveAgentConfig.Source.PROJECT);
    assertThat(cfg.modelId()).isEqualTo("test-model-x");
    assertThat(cfg.systemPrompt()).startsWith("You are functional.");
    assertThat(cfg.maxRounds()).isEqualTo(2);
  }

  @Test
  void orgAssignmentResolvesWhenNoProjectAssignment() {
    UUID agentId = UUID.randomUUID();
    when(assignmentRepo.findByScopeAndScopeIdAndTaskTypeAndSubType(
            "PROJECT", projectId, TASK.name(), "DEFAULT"))
        .thenReturn(Optional.empty());
    when(assignmentRepo.findByScopeAndScopeIdAndTaskTypeAndSubType(
            "ORG", orgId, TASK.name(), "DEFAULT"))
        .thenReturn(
            Optional.of(
                new TaskAgentAssignment("ORG", orgId, TASK.name(), "DEFAULT", agentId, true, "u")));
    Agent orgAgent =
        new Agent(
            "ORG",
            orgId,
            "OrgDefault",
            "d",
            null,
            null,
            null,
            null,
            "STANDARD",
            null,
            null,
            1,
            true,
            "u");
    when(agentRepo.findById(agentId)).thenReturn(Optional.of(orgAgent));

    EffectiveAgentConfig cfg = service.resolve(projectId, TASK, "DEFAULT", null);

    assertThat(cfg.source()).isEqualTo(EffectiveAgentConfig.Source.ORG);
    assertThat(cfg.modelRole()).isEqualTo("STANDARD");
  }

  // ── explicit selection ───────────────────────────────────────────────────────

  @Test
  void explicitAgentBypassesCascadeAndComposesTemplateAndSkills() {
    UUID agentId = UUID.randomUUID();
    UUID templateId = UUID.randomUUID();
    UUID skillId = UUID.randomUUID();
    Agent agent =
        new Agent(
            "PROJECT",
            projectId,
            "Custom",
            "d",
            "You are a senior SDET.",
            templateId,
            null,
            "[\"" + skillId + "\"]",
            null,
            "gpt-4o",
            null,
            4,
            true,
            "u");
    when(agentRepo.findById(agentId)).thenReturn(Optional.of(agent));

    AiPromptTemplate tpl =
        new AiPromptTemplate(projectId, "SYSTEM", "T", "TEMPLATE BODY", false, "u");
    when(templateRepo.findById(templateId)).thenReturn(Optional.of(tpl));
    AiSkill skill = new AiSkill(projectId, "Edge cases", "d", "Cover boundary values.", true, "u");
    when(skillRepo.findById(skillId)).thenReturn(Optional.of(skill));

    EffectiveAgentConfig cfg = service.resolve(projectId, TASK, "DEFAULT", agentId);

    assertThat(cfg.source()).isEqualTo(EffectiveAgentConfig.Source.PROJECT);
    assertThat(cfg.modelId()).isEqualTo("gpt-4o");
    assertThat(cfg.modelRole()).isNull();
    assertThat(cfg.systemPrompt())
        .startsWith("You are a senior SDET.")
        .contains("TEMPLATE BODY")
        .contains("## Applied skills")
        .contains("Cover boundary values.");
    // The un-persisted AiSkill has a null id in this unit test; one skill resolved is the
    // assertion.
    assertThat(cfg.skillIds()).hasSize(1);
    assertThat(cfg.maxRounds()).isEqualTo(4);
  }

  @Test
  void explicitAgentNotVisibleToProjectThrows() {
    UUID agentId = UUID.randomUUID();
    UUID otherProject = UUID.randomUUID();
    Agent foreign =
        new Agent(
            "PROJECT",
            otherProject,
            "Foreign",
            "d",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            1,
            true,
            "u");
    when(agentRepo.findById(agentId)).thenReturn(Optional.of(foreign));

    assertThatThrownBy(() -> service.resolve(projectId, TASK, "DEFAULT", agentId))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("not visible");
  }

  // ── sub-type defaulting ──────────────────────────────────────────────────────

  @Test
  void blankSubTypeResolvesToTaskDefault() {
    when(subTypeRepo.findByTaskTypeOrderByKeyAsc(TASK.name()))
        .thenReturn(
            List.of(
                new TaskSubType(TASK.name(), "FUNCTIONAL", "Functional", true),
                new TaskSubType(TASK.name(), "NON_FUNCTIONAL", "Non-functional", false)));
    // Assignment exists only for the FUNCTIONAL default — proves the resolved sub-type was
    // FUNCTIONAL.
    UUID agentId = UUID.randomUUID();
    when(assignmentRepo.findByScopeAndScopeIdAndTaskTypeAndSubType(
            eq("PROJECT"), eq(projectId), eq(TASK.name()), eq("FUNCTIONAL")))
        .thenReturn(
            Optional.of(
                new TaskAgentAssignment(
                    "PROJECT", projectId, TASK.name(), "FUNCTIONAL", agentId, true, "u")));
    when(agentRepo.findById(agentId)).thenReturn(Optional.of(projectAgent("F", null, null, null)));

    EffectiveAgentConfig cfg = service.resolve(projectId, TASK, null, null);

    assertThat(cfg.source()).isEqualTo(EffectiveAgentConfig.Source.PROJECT);
  }
}
