package com.platform.ingestion.management.tcm;

import com.platform.core.domain.TestCaseProperty;
import com.platform.core.repository.TestCasePropertyRepository;
import com.platform.security.authz.Capability;
import com.platform.security.web.RequireCapability;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Parametrization properties (axes) on a test case. These define the values the property matrix
 * expands into per-combination executions at run creation.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/test-cases/{caseId}/properties")
@Tag(name = "Test Case Management")
@RequireCapability(value = Capability.VIEW_RESULTS, scope = "projectId")
public class TestCasePropertyController {

  public record PropertyDto(String name, String value) {}

  private final TestCasePropertyService service;

  public TestCasePropertyController(TestCasePropertyService service) {
    this.service = service;
  }

  @GetMapping
  public List<PropertyDto> list(@PathVariable UUID projectId, @PathVariable UUID caseId) {
    return service.list(caseId);
  }

  /** Replaces all properties for the case. */
  @PutMapping
  @RequireCapability(value = Capability.OPERATE_QUALITY, scope = "projectId")
  public List<PropertyDto> replace(
      @PathVariable UUID projectId,
      @PathVariable UUID caseId,
      @RequestBody List<PropertyDto> properties) {
    return service.replace(caseId, properties);
  }

  @Service
  @Transactional
  static class TestCasePropertyService {
    private final TestCasePropertyRepository repo;

    TestCasePropertyService(TestCasePropertyRepository repo) {
      this.repo = repo;
    }

    @Transactional(readOnly = true)
    List<PropertyDto> list(UUID caseId) {
      return repo.findByTestCaseId(caseId).stream()
          .map(p -> new PropertyDto(p.getName(), p.getValue()))
          .toList();
    }

    List<PropertyDto> replace(UUID caseId, List<PropertyDto> properties) {
      repo.findByTestCaseId(caseId).forEach(repo::delete);
      if (properties != null) {
        for (PropertyDto p : properties) {
          if (p.name() != null
              && !p.name().isBlank()
              && p.value() != null
              && !p.value().isBlank()) {
            repo.save(new TestCaseProperty(caseId, p.name().trim(), p.value().trim()));
          }
        }
      }
      return list(caseId);
    }
  }
}
