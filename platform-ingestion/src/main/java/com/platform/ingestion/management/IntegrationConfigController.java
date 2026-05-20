package com.platform.ingestion.management;

import com.platform.ingestion.management.dto.IntegrationConfigDto;
import com.platform.ingestion.management.dto.SaveIntegrationConfigRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/integrations")
@Tag(name = "Integration Configs")
public class IntegrationConfigController {

    private final IntegrationConfigService service;

    public IntegrationConfigController(IntegrationConfigService service) {
        this.service = service;
    }

    @GetMapping
    public List<IntegrationConfigDto> list(@PathVariable UUID projectId) {
        return service.list(projectId);
    }

    @PostMapping
    public IntegrationConfigDto save(@PathVariable UUID projectId,
                                     @Valid @RequestBody SaveIntegrationConfigRequest req) {
        return service.save(projectId, req);
    }

    @DeleteMapping("/{configId}")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID configId) {
        service.delete(projectId, configId);
        return ResponseEntity.noContent().build();
    }
}
