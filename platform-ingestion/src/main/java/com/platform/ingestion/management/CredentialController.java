package com.platform.ingestion.management;

import com.platform.ingestion.management.dto.CredentialDto;
import com.platform.ingestion.management.dto.SaveCredentialRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin API for scoped, encrypted integration credentials — the centralized
 * "Admin PAT" plus Team/Project overrides resolved by the Org→Team→Project cascade.
 */
@RestController
@RequestMapping("/api/v1/credentials")
@Tag(name = "Integration Credentials")
public class CredentialController {

    private final CredentialService service;

    public CredentialController(CredentialService service) {
        this.service = service;
    }

    /** List credentials at a scope. ORG: no scopeId; TEAM/PROJECT: scopeId required. */
    @GetMapping
    public List<CredentialDto> list(@RequestParam String scope,
                                    @RequestParam(required = false) UUID scopeId) {
        return service.list(scope, scopeId);
    }

    @PostMapping
    public CredentialDto save(@Valid @RequestBody SaveCredentialRequest req,
                              @RequestHeader(value = "X-Actor", required = false) String actor) {
        return service.save(req, actor != null ? actor : "portal");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    public Map<String, Object> testConnection(@PathVariable UUID id) {
        CredentialHealthChecker.Result r = service.testConnection(id);
        return Map.of("ok", r.ok(), "message", r.message());
    }
}
