package com.platform.ingestion.security;

import com.platform.core.domain.ApiKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * CRUD endpoints for API key management.
 *
 * <pre>
 * POST   /api/v1/api-keys              — create a new key for a team
 * GET    /api/v1/api-keys?teamId=...   — list active keys for a team
 * DELETE /api/v1/api-keys/{id}         — revoke a key
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/api-keys")
@Tag(name = "API Keys", description = "API key management for team authentication")
public class ApiKeyController {

    private final ApiKeyService keyService;

    public ApiKeyController(ApiKeyService keyService) {
        this.keyService = keyService;
    }

    @PostMapping
    @Operation(summary = "Create a new API key for a team")
    public ResponseEntity<ApiKeyService.ApiKeyCreationResult> create(
            @RequestBody CreateApiKeyRequest request) {
        ApiKeyService.ApiKeyCreationResult result = keyService.create(
                request.name(), request.teamId(), request.ttlDays());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    @Operation(summary = "List active API keys for a team")
    public List<ApiKeySummary> list(@RequestParam UUID teamId) {
        return keyService.listForTeam(teamId).stream()
                .map(ApiKeySummary::from)
                .toList();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke an API key")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        // In a real system, extract actorKeyId from the security context
        keyService.revoke(id, null);
        return ResponseEntity.noContent().build();
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record CreateApiKeyRequest(String name, UUID teamId, Integer ttlDays) {}

    public record ApiKeySummary(
            UUID id, String name, String prefix, UUID teamId,
            java.time.Instant expiresAt, java.time.Instant lastUsedAt,
            java.time.Instant createdAt) {
        static ApiKeySummary from(ApiKey k) {
            return new ApiKeySummary(k.getId(), k.getName(), k.getKeyPrefix(),
                    k.getTeamId(), k.getExpiresAt(), k.getLastUsedAt(), k.getCreatedAt());
        }
    }
}
