package com.platform.ingestion.management.tcm;

import com.platform.security.authz.Capability;
import com.platform.security.jwt.AuthenticatedUser;
import com.platform.security.web.CurrentUser;
import com.platform.security.web.RequireCapability;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Execution planning reads: a tester's "My Queue" and the assignable-user picker. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
@RequireCapability(value = Capability.VIEW_RESULTS, scope = "projectId")
@Tag(name = "Execution Planning")
public class ExecutionPlanningController {

  private final ExecutionPlanningService service;

  public ExecutionPlanningController(ExecutionPlanningService service) {
    this.service = service;
  }

  @GetMapping("/my-queue")
  public List<TestCaseExecutionDto> myQueue(@PathVariable UUID projectId) {
    AuthenticatedUser user = CurrentUser.get();
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
    }
    return service.myQueue(projectId, user.userId());
  }

  @GetMapping("/assignable-users")
  public List<ExecutionPlanningService.AssignableUser> assignableUsers(
      @PathVariable UUID projectId) {
    return service.assignableUsers();
  }
}
