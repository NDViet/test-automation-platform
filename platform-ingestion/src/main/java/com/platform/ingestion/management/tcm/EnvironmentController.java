package com.platform.ingestion.management.tcm;

import com.platform.security.authz.Capability;
import com.platform.security.web.RequireCapability;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/environments")
@Tag(name = "Test Case Management")
@RequireCapability(value = Capability.VIEW_RESULTS, scope = "projectId")
public class EnvironmentController {

  private final EnvironmentService service;

  public EnvironmentController(EnvironmentService service) {
    this.service = service;
  }

  @GetMapping
  public List<EnvironmentDto> list(@PathVariable UUID projectId) {
    return service.list(projectId);
  }

  @PostMapping
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public ResponseEntity<EnvironmentDto> create(
      @PathVariable UUID projectId, @Valid @RequestBody CreateEnvironmentRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(projectId, req));
  }

  @DeleteMapping("/{envId}")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID envId) {
    service.delete(projectId, envId);
    return ResponseEntity.noContent().build();
  }
}
