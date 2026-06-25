package com.platform.ingestion.management;

import com.platform.ingestion.management.dto.CreateOrganizationRequest;
import com.platform.ingestion.management.dto.UpdateOrganizationRequest;
import com.platform.ingestion.query.dto.OrganizationDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = "Organization Management")
public class OrganizationManagementController {

  private final OrganizationManagementService service;

  public OrganizationManagementController(OrganizationManagementService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<OrganizationDto> create(@Valid @RequestBody CreateOrganizationRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
  }

  @PutMapping("/{id}")
  public OrganizationDto update(@PathVariable UUID id, @RequestBody UpdateOrganizationRequest req) {
    return service.update(id, req);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/logo")
  public OrganizationDto uploadLogo(@PathVariable UUID id, @RequestParam("file") MultipartFile file)
      throws IOException {
    return service.uploadLogo(id, file.getBytes(), file.getContentType());
  }

  @GetMapping("/{id}/logo")
  public ResponseEntity<byte[]> getLogo(@PathVariable UUID id) {
    return service
        .getLogo(id)
        .map(
            r ->
                ResponseEntity.ok()
                    .header("Content-Type", r.contentType())
                    .header("Cache-Control", "max-age=86400")
                    .body(r.bytes()))
        .orElse(ResponseEntity.notFound().build());
  }
}
