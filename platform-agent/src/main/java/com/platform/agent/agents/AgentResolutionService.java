package com.platform.agent.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.agents.EffectiveAgentConfig.Source;
import com.platform.agent.api.AiPromptTemplateService;
import com.platform.common.agent.AgentTaskType;
import com.platform.core.domain.Agent;
import com.platform.core.domain.AiPromptTemplate;
import com.platform.core.domain.AiSkill;
import com.platform.core.domain.Project;
import com.platform.core.domain.TaskAgentAssignment;
import com.platform.core.repository.AgentRepository;
import com.platform.core.repository.AiPromptTemplateRepository;
import com.platform.core.repository.AiSkillRepository;
import com.platform.core.repository.ProjectRepository;
import com.platform.core.repository.TaskAgentAssignmentRepository;
import com.platform.core.repository.TaskSubTypeRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The heart of agent management: resolves which agent runs a (project, task, sub-type) — explicit
 * selection → PROJECT assignment → ORG assignment → built-in seed — and assembles its {@link
 * EffectiveAgentConfig} (composed system prompt = persona + template/seed body + applied skills,
 * resolved user prompt, model, context, rounds). Pure read-side logic — no LLM, no mutation — so it
 * is fully unit-testable.
 */
@Service
public class AgentResolutionService {

  private static final Logger log = LoggerFactory.getLogger(AgentResolutionService.class);

  private static final String SCOPE_ORG = "ORG";
  private static final String SCOPE_PROJECT = "PROJECT";
  private static final String DEFAULT_SUB_TYPE = "DEFAULT";

  private final AgentRepository agentRepo;
  private final TaskAgentAssignmentRepository assignmentRepo;
  private final TaskSubTypeRepository subTypeRepo;
  private final AiPromptTemplateRepository templateRepo;
  private final AiSkillRepository skillRepo;
  private final ProjectRepository projectRepo;
  private final SeedAgentCatalog seedCatalog;
  private final AiPromptTemplateService promptTemplateService;
  private final ObjectMapper mapper;

  public AgentResolutionService(
      AgentRepository agentRepo,
      TaskAgentAssignmentRepository assignmentRepo,
      TaskSubTypeRepository subTypeRepo,
      AiPromptTemplateRepository templateRepo,
      AiSkillRepository skillRepo,
      ProjectRepository projectRepo,
      SeedAgentCatalog seedCatalog,
      AiPromptTemplateService promptTemplateService,
      ObjectMapper mapper) {
    this.agentRepo = agentRepo;
    this.assignmentRepo = assignmentRepo;
    this.subTypeRepo = subTypeRepo;
    this.templateRepo = templateRepo;
    this.skillRepo = skillRepo;
    this.projectRepo = projectRepo;
    this.seedCatalog = seedCatalog;
    this.promptTemplateService = promptTemplateService;
    this.mapper = mapper;
  }

  /**
   * Resolve the effective agent config for a project task. {@code subType} null/blank ⇒ the task's
   * default sub-type. {@code explicitAgentId} non-null ⇒ that agent is used (must be visible to the
   * project), bypassing the assignment cascade.
   */
  @Transactional(readOnly = true)
  public EffectiveAgentConfig resolve(
      UUID projectId, AgentTaskType taskType, String subType, UUID explicitAgentId) {
    Project project =
        projectRepo
            .findById(projectId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    UUID orgId = project.getOrganization().getId();
    String effSub = effectiveSubType(taskType, subType);

    // 1. Explicit selection.
    if (explicitAgentId != null) {
      Agent agent =
          agentRepo
              .findById(explicitAgentId)
              .orElseThrow(
                  () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));
      requireVisible(agent, projectId, orgId);
      return assemble(agent, taskType, projectId, orgId, sourceOf(agent));
    }

    // 2. Project assignment.
    Optional<Agent> projectAgent =
        enabledAssignment(SCOPE_PROJECT, projectId, taskType, effSub).flatMap(this::loadAgent);
    if (projectAgent.isPresent()) {
      return assemble(projectAgent.get(), taskType, projectId, orgId, Source.PROJECT);
    }

    // 3. Org assignment.
    Optional<Agent> orgAgent =
        enabledAssignment(SCOPE_ORG, orgId, taskType, effSub).flatMap(this::loadAgent);
    if (orgAgent.isPresent()) {
      return assemble(orgAgent.get(), taskType, projectId, orgId, Source.ORG);
    }

    // 4. Built-in seed.
    return assembleSeed(taskType, projectId);
  }

  // ── sub-type ──────────────────────────────────────────────────────────────

  /** The effective sub-type: the requested one, else the task's default, else {@code DEFAULT}. */
  public String effectiveSubType(AgentTaskType taskType, String requested) {
    if (requested != null && !requested.isBlank()) {
      return requested.trim();
    }
    return subTypeRepo.findByTaskTypeOrderByKeyAsc(taskType.name()).stream()
        .filter(com.platform.core.domain.TaskSubType::isDefault)
        .findFirst()
        .map(com.platform.core.domain.TaskSubType::getKey)
        .orElse(DEFAULT_SUB_TYPE);
  }

  // ── assembly ──────────────────────────────────────────────────────────────

  private EffectiveAgentConfig assemble(
      Agent agent, AgentTaskType taskType, UUID projectId, UUID orgId, Source source) {
    SeedAgentCatalog.SeedAgent seed = seedCatalog.seedFor(taskType);

    String systemBody =
        agent.getSystemTemplateId() != null
            ? templateBody(
                agent.getSystemTemplateId(), projectId, orgId, seedSystemBody(taskType, projectId))
            : seedSystemBody(taskType, projectId);
    String userBody =
        agent.getUserTemplateId() != null
            ? templateBody(
                agent.getUserTemplateId(), projectId, orgId, seedUserBody(taskType, projectId))
            : seedUserBody(taskType, projectId);

    List<AiSkill> skills = visibleSkills(agent, projectId, orgId);
    String systemPrompt = composeSystem(agent.getPersona(), systemBody, skills);

    return new EffectiveAgentConfig(
        source,
        agent.getId(),
        systemPrompt,
        userBody,
        agent.getModelId(),
        agent.getModelRole(),
        skills.stream().map(AiSkill::getId).toList(),
        agent.getContextConfig() != null ? agent.getContextConfig() : seed.contextConfig(),
        agent.getMaxRounds());
  }

  private EffectiveAgentConfig assembleSeed(AgentTaskType taskType, UUID projectId) {
    SeedAgentCatalog.SeedAgent seed = seedCatalog.seedFor(taskType);
    return new EffectiveAgentConfig(
        Source.SEED,
        null,
        seedSystemBody(taskType, projectId),
        seedUserBody(taskType, projectId),
        null,
        seed.modelRole(),
        List.of(),
        seed.contextConfig(),
        seed.maxRounds());
  }

  /**
   * Seed system body. For test-case generation this is the project's <em>default</em> SYSTEM
   * template (or the built-in seed prompt when none) — preserving today's behavior so an
   * unconfigured run matches the existing output. Other tasks use the generic catalog seed.
   */
  private String seedSystemBody(AgentTaskType taskType, UUID projectId) {
    return isGeneration(taskType)
        ? promptTemplateService.resolveDefault(projectId, AiPromptTemplateService.KIND_SYSTEM)
        : seedCatalog.seedFor(taskType).systemPrompt();
  }

  private String seedUserBody(AgentTaskType taskType, UUID projectId) {
    return isGeneration(taskType)
        ? promptTemplateService.resolveDefault(projectId, AiPromptTemplateService.KIND_USER)
        : seedCatalog.seedFor(taskType).userPrompt();
  }

  private static boolean isGeneration(AgentTaskType taskType) {
    return taskType == AgentTaskType.GENERATE_TEST_CASES
        || taskType == AgentTaskType.GENERATE_MANUAL_TEST_CASES;
  }

  private String composeSystem(String persona, String systemBody, List<AiSkill> skills) {
    StringBuilder sb = new StringBuilder();
    if (notBlank(persona)) {
      sb.append(persona.trim()).append("\n\n");
    }
    sb.append(systemBody == null ? "" : systemBody.trim());
    if (!skills.isEmpty()) {
      sb.append("\n\n## Applied skills");
      for (AiSkill skill : skills) {
        sb.append("\n\n### ").append(skill.getName()).append("\n").append(skill.getInstructions());
      }
    }
    return sb.toString();
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private Optional<TaskAgentAssignment> enabledAssignment(
      String scope, UUID scopeId, AgentTaskType taskType, String subType) {
    return assignmentRepo
        .findByScopeAndScopeIdAndTaskTypeAndSubType(scope, scopeId, taskType.name(), subType)
        .filter(TaskAgentAssignment::isEnabled);
  }

  private Optional<Agent> loadAgent(TaskAgentAssignment a) {
    Optional<Agent> agent = agentRepo.findById(a.getAgentId()).filter(Agent::isEnabled);
    if (agent.isEmpty()) {
      log.warn("assignment {} points at a missing/disabled agent {}", a.getId(), a.getAgentId());
    }
    return agent;
  }

  /** Body of a referenced template if it exists and is visible; else the seed fallback. */
  private String templateBody(UUID templateId, UUID projectId, UUID orgId, String fallback) {
    return templateRepo
        .findById(templateId)
        .filter(t -> isVisible(t.getScope(), t.getScopeId(), projectId, orgId))
        .map(AiPromptTemplate::getBody)
        .orElseGet(
            () -> {
              log.warn(
                  "agent references missing/out-of-scope template {} — using seed prompt",
                  templateId);
              return fallback;
            });
  }

  private List<AiSkill> visibleSkills(Agent agent, UUID projectId, UUID orgId) {
    List<UUID> ids = parseIds(agent.getSkillIdsJson());
    List<AiSkill> out = new ArrayList<>();
    for (UUID id : ids) {
      skillRepo
          .findById(id)
          .filter(AiSkill::isEnabled)
          .filter(s -> isVisible(s.getScope(), s.getScopeId(), projectId, orgId))
          .ifPresent(out::add);
    }
    return out;
  }

  private void requireVisible(Agent agent, UUID projectId, UUID orgId) {
    if (!isVisible(agent.getScope(), agent.getScopeId(), projectId, orgId)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Agent is not visible to this project");
    }
  }

  /** A scoped artifact is visible to a project when it is the project's, or its org's. */
  private boolean isVisible(String scope, UUID scopeId, UUID projectId, UUID orgId) {
    if (SCOPE_PROJECT.equals(scope)) return projectId.equals(scopeId);
    if (SCOPE_ORG.equals(scope)) return orgId.equals(scopeId);
    return false;
  }

  private Source sourceOf(Agent agent) {
    return SCOPE_ORG.equals(agent.getScope()) ? Source.ORG : Source.PROJECT;
  }

  private List<UUID> parseIds(String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      String[] arr = mapper.readValue(json, String[].class);
      List<UUID> out = new ArrayList<>();
      for (String s : arr) {
        if (s != null && !s.isBlank()) {
          try {
            out.add(UUID.fromString(s.trim()));
          } catch (IllegalArgumentException ignored) {
            // skip malformed id
          }
        }
      }
      return out;
    } catch (Exception e) {
      return List.of();
    }
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }
}
