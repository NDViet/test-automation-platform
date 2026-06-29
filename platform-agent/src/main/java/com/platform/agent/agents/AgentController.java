package com.platform.agent.agents;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * CRUD for agents at org or project scope. {@code scope} path segment is {@code orgs} or {@code
 * projects} (mapped to ORG/PROJECT). Effective list (project ∪ inherited org) has its own route.
 */
@RestController
public class AgentController {

  private final AgentService service;

  public AgentController(AgentService service) {
    this.service = service;
  }

  @GetMapping("/hub/{scope}/{scopeId}/ai/agents")
  public List<AgentDto> list(@PathVariable String scope, @PathVariable UUID scopeId) {
    return service.list(toScope(scope), scopeId);
  }

  @GetMapping("/hub/{scope}/{scopeId}/ai/agents/{id}")
  public AgentDto get(
      @PathVariable String scope, @PathVariable UUID scopeId, @PathVariable UUID id) {
    return service.get(toScope(scope), scopeId, id);
  }

  @PostMapping("/hub/{scope}/{scopeId}/ai/agents")
  public ResponseEntity<AgentDto> create(
      @PathVariable String scope,
      @PathVariable UUID scopeId,
      @RequestBody AgentRequest req,
      @RequestHeader(value = "X-Actor", required = false) String actor) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.create(toScope(scope), scopeId, req, actor));
  }

  @PutMapping("/hub/{scope}/{scopeId}/ai/agents/{id}")
  public AgentDto update(
      @PathVariable String scope,
      @PathVariable UUID scopeId,
      @PathVariable UUID id,
      @RequestBody AgentRequest req,
      @RequestHeader(value = "X-Actor", required = false) String actor) {
    return service.update(toScope(scope), scopeId, id, req, actor);
  }

  @DeleteMapping("/hub/{scope}/{scopeId}/ai/agents/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable String scope,
      @PathVariable UUID scopeId,
      @PathVariable UUID id,
      @RequestHeader(value = "X-Actor", required = false) String actor) {
    service.delete(toScope(scope), scopeId, id, actor);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/hub/projects/{projectId}/ai/agents/effective")
  public List<AgentDto> effective(@PathVariable UUID projectId) {
    return service.effectiveForProject(projectId);
  }

  private static String toScope(String pathScope) {
    return switch (pathScope) {
      case "orgs" -> "ORG";
      case "projects" -> "PROJECT";
      default ->
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, "scope must be 'orgs' or 'projects'");
    };
  }
}
