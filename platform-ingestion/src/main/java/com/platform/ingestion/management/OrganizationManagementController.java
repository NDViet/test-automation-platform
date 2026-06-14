package com.platform.ingestion.management;

import com.platform.ingestion.management.dto.CreateOrganizationRequest;
import com.platform.ingestion.management.dto.UpdateOrganizationRequest;
import com.platform.ingestion.query.dto.OrganizationDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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
}
