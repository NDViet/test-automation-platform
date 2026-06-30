package com.platform.agent.agents;

import com.platform.agent.agents.TaskAgentDtos.EffectiveAssignmentDto;
import com.platform.agent.agents.TaskAgentDtos.TaskAgentDto;
import com.platform.agent.agents.TaskAgentDtos.TaskAgentRequest;
import com.platform.agent.agents.TaskAgentDtos.TaskSubTypeDto;
import com.platform.security.web.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Task→agent assignment management, the sub-type catalog, and effective resolution. */
@RestController
public class TaskAgentController {

  private final TaskAgentService service;

  public TaskAgentController(TaskAgentService service) {
    this.service = service;
  }

  @GetMapping("/hub/{scope}/{scopeId}/ai/task-agents")
  public List<TaskAgentDto> list(@PathVariable String scope, @PathVariable UUID scopeId) {
    return service.list(toScope(scope), scopeId);
  }

  @PutMapping("/hub/{scope}/{scopeId}/ai/task-agents")
  public TaskAgentDto upsert(
      @PathVariable String scope, @PathVariable UUID scopeId, @RequestBody TaskAgentRequest req) {
    String actor = CurrentUser.username();
    return service.upsert(toScope(scope), scopeId, req, actor);
  }

  @DeleteMapping("/hub/{scope}/{scopeId}/ai/task-agents/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable String scope, @PathVariable UUID scopeId, @PathVariable UUID id) {
    String actor = CurrentUser.username();
    service.delete(toScope(scope), scopeId, id, actor);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/hub/ai/task-subtypes")
  public List<TaskSubTypeDto> subTypes(@RequestParam String taskType) {
    return service.subTypes(taskType);
  }

  @GetMapping("/hub/projects/{projectId}/ai/task-agents/effective")
  public EffectiveAssignmentDto effective(
      @PathVariable UUID projectId,
      @RequestParam String taskType,
      @RequestParam(required = false) String subType) {
    return service.effective(projectId, taskType, subType);
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
