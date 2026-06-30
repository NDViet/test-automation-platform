package com.platform.ingestion.management.release;

import com.platform.security.authz.Capability;
import com.platform.security.web.RequireCapability;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** CRUD for platform-owned releases (+ optional upstream mapping). */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/releases")
@Tag(name = "Releases")
@RequireCapability(value = Capability.VIEW_RESULTS, scope = "projectId")
public class ReleaseController {

  private final ReleaseService service;

  public ReleaseController(ReleaseService service) {
    this.service = service;
  }

  @GetMapping
  public List<ReleaseDto> list(@PathVariable UUID projectId) {
    return service.list(projectId);
  }

  @PostMapping
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public ResponseEntity<ReleaseDto> create(
      @PathVariable UUID projectId, @Valid @RequestBody CreateReleaseRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(projectId, req));
  }

  @GetMapping("/{id}")
  public ReleaseDto get(@PathVariable UUID projectId, @PathVariable UUID id) {
    return service.get(projectId, id);
  }

  @PutMapping("/{id}")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public ReleaseDto update(
      @PathVariable UUID projectId,
      @PathVariable UUID id,
      @Valid @RequestBody CreateReleaseRequest req) {
    return service.update(projectId, id, req);
  }

  @DeleteMapping("/{id}")
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID id) {
    service.delete(projectId, id);
    return ResponseEntity.noContent().build();
  }
}
