package com.platform.agent.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Project-scoped CRUD for reusable AI generation skills. */
@RestController
@RequestMapping("/hub/projects/{projectId}/ai/skills")
public class AiSkillController {

  private final AiSkillService service;

  public AiSkillController(AiSkillService service) {
    this.service = service;
  }

  @GetMapping
  public List<AiSkillDto> list(@PathVariable UUID projectId) {
    return service.list(projectId);
  }

  @GetMapping("/{skillId}")
  public AiSkillDto get(@PathVariable UUID projectId, @PathVariable UUID skillId) {
    return service.get(projectId, skillId);
  }

  @PostMapping
  public ResponseEntity<AiSkillDto> create(
      @PathVariable UUID projectId,
      @RequestBody AiSkillRequest req,
      @RequestHeader(value = "X-Actor", required = false) String actor) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(projectId, req, actor));
  }

  @PutMapping("/{skillId}")
  public AiSkillDto update(
      @PathVariable UUID projectId,
      @PathVariable UUID skillId,
      @RequestBody AiSkillRequest req,
      @RequestHeader(value = "X-Actor", required = false) String actor) {
    return service.update(projectId, skillId, req, actor);
  }

  @DeleteMapping("/{skillId}")
  public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID skillId) {
    service.delete(projectId, skillId);
    return ResponseEntity.noContent().build();
  }
}
