package com.platform.agent.agents;

import com.platform.agent.agents.TaskAgentDtos.EffectiveAssignmentDto;
import com.platform.agent.agents.TaskAgentDtos.TaskAgentDto;
import com.platform.agent.agents.TaskAgentDtos.TaskAgentRequest;
import com.platform.agent.agents.TaskAgentDtos.TaskSubTypeDto;
import com.platform.common.agent.AgentTaskType;
import com.platform.core.domain.Agent;
import com.platform.core.domain.Project;
import com.platform.core.domain.TaskAgentAssignment;
import com.platform.core.repository.AgentRepository;
import com.platform.core.repository.ProjectRepository;
import com.platform.core.repository.TaskAgentAssignmentRepository;
import com.platform.core.repository.TaskSubTypeRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Manages task→agent assignments (the default agent per scope/task/sub-type) and exposes the
 * sub-type catalog and the resolved "effective" default for a project task. Assignment writes are
 * guarded so an agent can only be bound where it is visible.
 */
@Service
public class TaskAgentService {

  static final String SCOPE_ORG = "ORG";
  static final String SCOPE_PROJECT = "PROJECT";

  private final TaskAgentAssignmentRepository repo;
  private final TaskSubTypeRepository subTypeRepo;
  private final AgentRepository agentRepo;
  private final ProjectRepository projectRepo;
  private final AgentResolutionService resolutionService;
  private final AgentRbacGuard rbacGuard;

  public TaskAgentService(
      TaskAgentAssignmentRepository repo,
      TaskSubTypeRepository subTypeRepo,
      AgentRepository agentRepo,
      ProjectRepository projectRepo,
      AgentResolutionService resolutionService,
      AgentRbacGuard rbacGuard) {
    this.repo = repo;
    this.subTypeRepo = subTypeRepo;
    this.agentRepo = agentRepo;
    this.projectRepo = projectRepo;
    this.resolutionService = resolutionService;
    this.rbacGuard = rbacGuard;
  }

  @Transactional(readOnly = true)
  public List<TaskAgentDto> list(String scope, UUID scopeId) {
    return repo.findByScopeAndScopeId(requireScope(scope), scopeId).stream()
        .map(TaskAgentService::toDto)
        .toList();
  }

  @Transactional
  public TaskAgentDto upsert(String scope, UUID scopeId, TaskAgentRequest req, String actor) {
    String s = requireScope(scope);
    rbacGuard.requireManage(s, scopeId, actor);
    String taskType = requireTaskType(req.taskType());
    String subType = req.subTypeOrDefault();
    Agent agent =
        agentRepo
            .findById(req.agentId())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));
    requireVisible(agent, s, scopeId);

    TaskAgentAssignment assignment =
        repo.findByScopeAndScopeIdAndTaskTypeAndSubType(s, scopeId, taskType, subType)
            .map(
                existing -> {
                  existing.reassign(req.agentId(), true);
                  return existing;
                })
            .orElseGet(
                () ->
                    new TaskAgentAssignment(
                        s, scopeId, taskType, subType, req.agentId(), true, actor));
    return toDto(repo.save(assignment));
  }

  @Transactional
  public void delete(String scope, UUID scopeId, UUID id, String actor) {
    String s = requireScope(scope);
    rbacGuard.requireManage(s, scopeId, actor);
    TaskAgentAssignment a =
        repo.findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
    if (!a.getScope().equals(s) || !a.getScopeId().equals(scopeId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found");
    }
    repo.delete(a);
  }

  /** Allowed sub-types for a task; an implicit single DEFAULT when the catalog has none. */
  @Transactional(readOnly = true)
  public List<TaskSubTypeDto> subTypes(String taskType) {
    String t = requireTaskType(taskType);
    List<TaskSubTypeDto> rows =
        subTypeRepo.findByTaskTypeOrderByKeyAsc(t).stream()
            .map(
                st ->
                    new TaskSubTypeDto(
                        st.getTaskType(), st.getKey(), st.getLabel(), st.isDefault()))
            .toList();
    return rows.isEmpty() ? List.of(new TaskSubTypeDto(t, "DEFAULT", "Default", true)) : rows;
  }

  /** The resolved default agent for a (project, task, subType): source + agent. */
  @Transactional(readOnly = true)
  public EffectiveAssignmentDto effective(UUID projectId, String taskType, String subType) {
    AgentTaskType task = AgentTaskType.valueOf(requireTaskType(taskType));
    EffectiveAgentConfig cfg = resolutionService.resolve(projectId, task, subType, null);
    String name =
        cfg.agentId() == null
            ? "(built-in seed)"
            : agentRepo.findById(cfg.agentId()).map(Agent::getName).orElse("(unknown)");
    return new EffectiveAssignmentDto(cfg.source().name(), cfg.agentId(), name);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private void requireVisible(Agent agent, String scope, UUID scopeId) {
    boolean visible;
    if (SCOPE_PROJECT.equals(scope)) {
      UUID orgId = orgIdOf(scopeId);
      visible =
          (SCOPE_PROJECT.equals(agent.getScope()) && scopeId.equals(agent.getScopeId()))
              || (SCOPE_ORG.equals(agent.getScope()) && orgId.equals(agent.getScopeId()));
    } else {
      visible = SCOPE_ORG.equals(agent.getScope()) && scopeId.equals(agent.getScopeId());
    }
    if (!visible) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Agent is not visible in this scope");
    }
  }

  private UUID orgIdOf(UUID projectId) {
    Project project =
        projectRepo
            .findById(projectId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    return project.getOrganization().getId();
  }

  private static TaskAgentDto toDto(TaskAgentAssignment a) {
    return new TaskAgentDto(
        a.getId(),
        a.getScope(),
        a.getScopeId(),
        a.getTaskType(),
        a.getSubType(),
        a.getAgentId(),
        a.isEnabled());
  }

  private static String requireScope(String scope) {
    String s = scope == null ? null : scope.trim().toUpperCase();
    if (!SCOPE_ORG.equals(s) && !SCOPE_PROJECT.equals(s)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scope must be ORG or PROJECT");
    }
    return s;
  }

  private static String requireTaskType(String taskType) {
    if (taskType == null || taskType.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "taskType is required");
    }
    try {
      return AgentTaskType.valueOf(taskType.trim()).name();
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown taskType: " + taskType);
    }
  }
}
