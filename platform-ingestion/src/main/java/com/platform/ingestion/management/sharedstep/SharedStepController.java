package com.platform.ingestion.management.sharedstep;

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

/** Shared-step library. Reads VIEW_RESULTS; mutations OPERATE_QUALITY. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/shared-steps")
@RequireCapability(value = Capability.VIEW_RESULTS, scope = "projectId")
@Tag(name = "Shared Steps")
public class SharedStepController {

  private final SharedStepService service;

  public SharedStepController(SharedStepService service) {
    this.service = service;
  }

  @GetMapping
  public List<SharedStepDto> list(@PathVariable UUID projectId) {
    return service.list(projectId);
  }

  @GetMapping("/{id}")
  public SharedStepDto get(@PathVariable UUID projectId, @PathVariable UUID id) {
    return service.get(projectId, id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public SharedStepDto create(
      @PathVariable UUID projectId, @Valid @RequestBody SharedStepForm req) {
    return service.create(projectId, req);
  }

  @PutMapping("/{id}")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public SharedStepDto update(
      @PathVariable UUID projectId, @PathVariable UUID id, @Valid @RequestBody SharedStepForm req) {
    return service.update(projectId, id, req);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public void delete(@PathVariable UUID projectId, @PathVariable UUID id) {
    service.delete(projectId, id);
  }
}
