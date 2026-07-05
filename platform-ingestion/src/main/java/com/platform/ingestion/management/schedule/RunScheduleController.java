package com.platform.ingestion.management.schedule;

import com.platform.ingestion.management.ProjectGitHubWorkflowService;
import com.platform.security.authz.Capability;
import com.platform.security.web.RequireCapability;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * CI run schedules. A stored schedule dispatches an external GitHub Actions workflow, so managing
 * one is project configuration — gated by MANAGE_PROJECT (stricter than an ad-hoc manual dispatch).
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/run-schedules")
@RequireCapability(value = Capability.MANAGE_PROJECT, scope = "projectId")
@Tag(name = "Run Schedules")
public class RunScheduleController {

  private final RunScheduleService service;
  private final RunScheduleDispatcher dispatcher;

  public RunScheduleController(RunScheduleService service, RunScheduleDispatcher dispatcher) {
    this.service = service;
    this.dispatcher = dispatcher;
  }

  @GetMapping
  public List<RunScheduleDto> list(@PathVariable UUID projectId) {
    return service.list(projectId);
  }

  @GetMapping("/{id}")
  public RunScheduleDto get(@PathVariable UUID projectId, @PathVariable UUID id) {
    return service.get(projectId, id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RunScheduleDto create(
      @PathVariable UUID projectId, @Valid @RequestBody RunScheduleForm req) {
    return service.create(projectId, req);
  }

  @PutMapping("/{id}")
  public RunScheduleDto update(
      @PathVariable UUID projectId,
      @PathVariable UUID id,
      @Valid @RequestBody RunScheduleForm req) {
    return service.update(projectId, id, req);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID projectId, @PathVariable UUID id) {
    service.delete(projectId, id);
  }

  /** Fire this schedule's workflow immediately (project-scoped load, then dispatch). */
  @PostMapping("/{id}/dispatch")
  public ProjectGitHubWorkflowService.DispatchResult dispatchNow(
      @PathVariable UUID projectId, @PathVariable UUID id) {
    return dispatcher.dispatch(service.load(projectId, id));
  }
}
