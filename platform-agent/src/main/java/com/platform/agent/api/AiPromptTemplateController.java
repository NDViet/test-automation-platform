package com.platform.agent.api;

import com.platform.security.web.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Project-scoped CRUD + default resolution for AI generation prompt templates. */
@RestController
@RequestMapping("/hub/projects/{projectId}/ai/prompt-templates")
public class AiPromptTemplateController {

  private final AiPromptTemplateService service;

  public AiPromptTemplateController(AiPromptTemplateService service) {
    this.service = service;
  }

  @GetMapping
  public List<AiPromptTemplateDto> list(@PathVariable UUID projectId) {
    return service.list(projectId);
  }

  /** Resolved default SYSTEM + USER prompt bodies (for pre-filling the generation form). */
  @GetMapping("/defaults")
  public PromptDefaults defaults(@PathVariable UUID projectId) {
    return new PromptDefaults(
        service.resolveDefault(projectId, AiPromptTemplateService.KIND_SYSTEM),
        service.resolveDefault(projectId, AiPromptTemplateService.KIND_USER));
  }

  @GetMapping("/{id}")
  public AiPromptTemplateDto get(@PathVariable UUID projectId, @PathVariable UUID id) {
    return service.get(projectId, id);
  }

  @PostMapping
  public ResponseEntity<AiPromptTemplateDto> create(
      @PathVariable UUID projectId,
      @RequestBody AiPromptTemplateRequest req) {
    String actor = CurrentUser.username();
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(projectId, req, actor));
  }

  @PutMapping("/{id}")
  public AiPromptTemplateDto update(
      @PathVariable UUID projectId,
      @PathVariable UUID id,
      @RequestBody AiPromptTemplateRequest req) {
    String actor = CurrentUser.username();
    return service.update(projectId, id, req, actor);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID id) {
    service.delete(projectId, id);
    return ResponseEntity.noContent().build();
  }

  public record PromptDefaults(String system, String user) {}
}
