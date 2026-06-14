package com.platform.ingestion.management.rbac;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * RBAC role administration. The {@code X-Actor} header identifies the calling
 * user; authorization is enforced in {@link RoleService} via {@code RbacService}.
 */
@RestController
@RequestMapping("/api/v1/rbac/members")
@Tag(name = "RBAC")
public class RoleController {

    private final RoleService service;

    public RoleController(RoleService service) {
        this.service = service;
    }

    @GetMapping
    public List<TeamMemberDto> list(@RequestParam String scope,
                                    @RequestParam(required = false) UUID scopeId) {
        return service.list(scope, scopeId);
    }

    @PostMapping
    public TeamMemberDto grant(@RequestHeader(value = "X-Actor", required = false) String actor,
                               @Valid @RequestBody GrantRoleRequest req) {
        return service.grant(actor != null ? actor : "", req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@RequestHeader(value = "X-Actor", required = false) String actor,
                                       @PathVariable UUID id) {
        service.revoke(actor != null ? actor : "", id);
        return ResponseEntity.noContent().build();
    }
}
