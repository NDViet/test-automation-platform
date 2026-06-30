package com.platform.agent.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.Agent;
import com.platform.core.domain.Project;
import com.platform.core.repository.AgentRepository;
import com.platform.core.repository.AiPromptTemplateRepository;
import com.platform.core.repository.AiSkillRepository;
import com.platform.core.repository.ProjectRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * CRUD for {@link Agent}s at ORG or PROJECT scope, plus the merged "effective" list a project sees
 * (its own agents ∪ inherited org agents, project shadowing org by name). Writes are guarded by
 * scope ownership and by reference visibility — an agent may only point at prompt templates /
 * skills visible in its scope.
 */
@Service
public class AgentService {

  static final String SCOPE_ORG = "ORG";
  static final String SCOPE_PROJECT = "PROJECT";

  private final AgentRepository repo;
  private final AiPromptTemplateRepository templateRepo;
  private final AiSkillRepository skillRepo;
  private final ProjectRepository projectRepo;
  private final AgentRbacGuard rbacGuard;
  private final ObjectMapper mapper;

  public AgentService(
      AgentRepository repo,
      AiPromptTemplateRepository templateRepo,
      AiSkillRepository skillRepo,
      ProjectRepository projectRepo,
      AgentRbacGuard rbacGuard,
      ObjectMapper mapper) {
    this.repo = repo;
    this.templateRepo = templateRepo;
    this.skillRepo = skillRepo;
    this.projectRepo = projectRepo;
    this.rbacGuard = rbacGuard;
    this.mapper = mapper;
  }

  @Transactional(readOnly = true)
  public List<AgentDto> list(String scope, UUID scopeId) {
    return repo.findByScopeAndScopeIdOrderByNameAsc(requireScope(scope), scopeId).stream()
        .map(a -> AgentDto.from(a, mapper))
        .toList();
  }

  @Transactional(readOnly = true)
  public AgentDto get(String scope, UUID scopeId, UUID id) {
    return AgentDto.from(loadOwned(requireScope(scope), scopeId, id), mapper);
  }

  /**
   * Project's own agents plus inherited org agents; a project agent shadows an org agent by name.
   */
  @Transactional(readOnly = true)
  public List<AgentDto> effectiveForProject(UUID projectId) {
    UUID orgId = orgIdOf(projectId);
    Map<String, AgentDto> byName = new LinkedHashMap<>();
    for (Agent org : repo.findByScopeAndScopeIdOrderByNameAsc(SCOPE_ORG, orgId)) {
      byName.put(org.getName(), AgentDto.inherited(org, mapper));
    }
    for (Agent proj : repo.findByScopeAndScopeIdOrderByNameAsc(SCOPE_PROJECT, projectId)) {
      byName.put(proj.getName(), AgentDto.from(proj, mapper)); // shadows org of the same name
    }
    return new ArrayList<>(byName.values());
  }

  @Transactional
  public AgentDto create(String scope, UUID scopeId, AgentRequest req, String actor) {
    String s = requireScope(scope);
    rbacGuard.requireManage(s, scopeId);
    String name = requireName(req.name());
    UUID orgId = s.equals(SCOPE_PROJECT) ? orgIdOf(scopeId) : scopeId;
    validateRefsVisible(s, scopeId, orgId, req);
    repo.findByScopeAndScopeIdAndName(s, scopeId, name)
        .ifPresent(
            x -> {
              throw new ResponseStatusException(
                  HttpStatus.CONFLICT,
                  "An agent named '" + name + "' already exists in this scope");
            });
    Agent agent =
        new Agent(
            s,
            scopeId,
            name,
            trimToNull(req.description()),
            trimToNull(req.persona()),
            req.systemTemplateId(),
            req.userTemplateId(),
            writeIds(req.skillIdsOrEmpty()),
            trimToNull(req.modelRole()),
            trimToNull(req.modelId()),
            req.contextConfig(),
            req.maxRoundsOrDefault(),
            req.enabledOrDefault(),
            actor);
    return AgentDto.from(repo.save(agent), mapper);
  }

  @Transactional
  public AgentDto update(String scope, UUID scopeId, UUID id, AgentRequest req, String actor) {
    String s = requireScope(scope);
    rbacGuard.requireManage(s, scopeId);
    Agent agent = loadOwned(s, scopeId, id);
    String name = requireName(req.name());
    UUID orgId = s.equals(SCOPE_PROJECT) ? orgIdOf(scopeId) : scopeId;
    validateRefsVisible(s, scopeId, orgId, req);
    repo.findByScopeAndScopeIdAndName(s, scopeId, name)
        .filter(other -> !other.getId().equals(id))
        .ifPresent(
            x -> {
              throw new ResponseStatusException(
                  HttpStatus.CONFLICT,
                  "An agent named '" + name + "' already exists in this scope");
            });
    agent.update(
        name,
        trimToNull(req.description()),
        trimToNull(req.persona()),
        req.systemTemplateId(),
        req.userTemplateId(),
        writeIds(req.skillIdsOrEmpty()),
        trimToNull(req.modelRole()),
        trimToNull(req.modelId()),
        req.contextConfig(),
        req.maxRoundsOrDefault(),
        req.enabledOrDefault());
    return AgentDto.from(repo.save(agent), mapper);
  }

  @Transactional
  public void delete(String scope, UUID scopeId, UUID id, String actor) {
    String s = requireScope(scope);
    rbacGuard.requireManage(s, scopeId);
    repo.delete(loadOwned(s, scopeId, id));
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private Agent loadOwned(String scope, UUID scopeId, UUID id) {
    Agent agent =
        repo.findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));
    if (!agent.getScope().equals(scope) || !agent.getScopeId().equals(scopeId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found");
    }
    return agent;
  }

  /**
   * Referenced templates/skills must be visible in the agent's scope (own scope or inherited org).
   */
  private void validateRefsVisible(String scope, UUID scopeId, UUID orgId, AgentRequest req) {
    if (req.systemTemplateId() != null) {
      templateRepo
          .findById(req.systemTemplateId())
          .filter(t -> visible(t.getScope(), t.getScopeId(), scope, scopeId, orgId))
          .orElseThrow(() -> outOfScope("system template"));
    }
    if (req.userTemplateId() != null) {
      templateRepo
          .findById(req.userTemplateId())
          .filter(t -> visible(t.getScope(), t.getScopeId(), scope, scopeId, orgId))
          .orElseThrow(() -> outOfScope("user template"));
    }
    for (String idStr : req.skillIdsOrEmpty()) {
      UUID skillId = parseUuid(idStr);
      skillRepo
          .findById(skillId)
          .filter(sk -> visible(sk.getScope(), sk.getScopeId(), scope, scopeId, orgId))
          .orElseThrow(() -> outOfScope("skill"));
    }
  }

  private boolean visible(
      String refScope, UUID refScopeId, String agentScope, UUID agentScopeId, UUID orgId) {
    if (SCOPE_PROJECT.equals(agentScope)) {
      return (SCOPE_PROJECT.equals(refScope) && agentScopeId.equals(refScopeId))
          || (SCOPE_ORG.equals(refScope) && orgId.equals(refScopeId));
    }
    return SCOPE_ORG.equals(refScope) && agentScopeId.equals(refScopeId);
  }

  private UUID orgIdOf(UUID projectId) {
    Project project =
        projectRepo
            .findById(projectId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    return project.getOrganization().getId();
  }

  private String writeIds(List<String> ids) {
    try {
      return mapper.writeValueAsString(ids);
    } catch (Exception e) {
      return "[]";
    }
  }

  private static ResponseStatusException outOfScope(String what) {
    return new ResponseStatusException(
        HttpStatus.BAD_REQUEST, "Referenced " + what + " is not visible in this scope");
  }

  private static UUID parseUuid(String s) {
    try {
      return UUID.fromString(s.trim());
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid skill id: " + s);
    }
  }

  private static String requireScope(String scope) {
    String s = scope == null ? null : scope.trim().toUpperCase();
    if (!SCOPE_ORG.equals(s) && !SCOPE_PROJECT.equals(s)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scope must be ORG or PROJECT");
    }
    return s;
  }

  private static String requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agent name is required");
    }
    return name.trim();
  }

  private static String trimToNull(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }
}
