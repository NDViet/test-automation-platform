package com.platform.agent.api;

import com.platform.core.domain.AiSkill;
import com.platform.core.repository.AiSkillRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Project-scoped CRUD for reusable AI generation skills. All reads/writes are guarded by projectId
 * so a skill can only be seen or mutated through the project that owns it.
 */
@Service
public class AiSkillService {

  private final AiSkillRepository repo;

  public AiSkillService(AiSkillRepository repo) {
    this.repo = repo;
  }

  @Transactional(readOnly = true)
  public List<AiSkillDto> list(UUID projectId) {
    return repo.findByProjectIdOrderByNameAsc(projectId).stream().map(AiSkillDto::from).toList();
  }

  @Transactional(readOnly = true)
  public AiSkillDto get(UUID projectId, UUID skillId) {
    return AiSkillDto.from(loadOwned(projectId, skillId));
  }

  @Transactional
  public AiSkillDto create(UUID projectId, AiSkillRequest req, String actor) {
    String name = requireName(req.name());
    requireInstructions(req.instructions());
    if (repo.existsByProjectIdAndName(projectId, name)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "A skill named '" + name + "' already exists in this project");
    }
    AiSkill skill =
        new AiSkill(
            projectId,
            name,
            trimToNull(req.description()),
            req.instructions(),
            req.enabledOrDefault(),
            actor);
    return AiSkillDto.from(repo.save(skill));
  }

  @Transactional
  public AiSkillDto update(UUID projectId, UUID skillId, AiSkillRequest req, String actor) {
    AiSkill skill = loadOwned(projectId, skillId);
    String name = requireName(req.name());
    requireInstructions(req.instructions());
    if (!name.equals(skill.getName()) && repo.existsByProjectIdAndName(projectId, name)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "A skill named '" + name + "' already exists in this project");
    }
    skill.update(name, trimToNull(req.description()), req.instructions(), req.enabledOrDefault());
    return AiSkillDto.from(repo.save(skill));
  }

  @Transactional
  public void delete(UUID projectId, UUID skillId) {
    repo.delete(loadOwned(projectId, skillId));
  }

  private AiSkill loadOwned(UUID projectId, UUID skillId) {
    AiSkill skill =
        repo.findById(skillId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found"));
    if (!skill.getProjectId().equals(projectId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found");
    }
    return skill;
  }

  private static String requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Skill name is required");
    }
    return name.trim();
  }

  private static void requireInstructions(String instructions) {
    if (instructions == null || instructions.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Skill instructions are required");
    }
  }

  private static String trimToNull(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }
}
