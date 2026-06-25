package com.platform.ingestion.management.tcm;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/coverage")
@Tag(name = "Test Case Management")
public class CoverageController {

  private final CoverageService service;

  public CoverageController(CoverageService service) {
    this.service = service;
  }

  @GetMapping
  public CoverageDto coverage(
      @PathVariable UUID projectId,
      @RequestParam(required = false) String area,
      @RequestParam(required = false) String team,
      @RequestParam(required = false) String iteration) {
    return service.coverage(projectId, area, team, iteration);
  }
}
