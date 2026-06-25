package com.platform.ingestion.query;

import com.platform.ingestion.query.dto.OrganizationDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = "Organizations", description = "Organization queries (ADO-first top tenant)")
public class OrganizationQueryController {

  private final OrganizationQueryService service;

  public OrganizationQueryController(OrganizationQueryService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "List all organizations")
  public List<OrganizationDto> listAll() {
    return service.findAll();
  }

  @GetMapping("/{slug}")
  @Operation(summary = "Get organization by slug")
  public ResponseEntity<OrganizationDto> getBySlug(@PathVariable String slug) {
    return service
        .findBySlug(slug)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }
}
