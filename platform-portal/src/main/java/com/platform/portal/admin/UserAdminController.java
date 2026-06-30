package com.platform.portal.admin;

import com.platform.portal.admin.UserAdminDtos.CreateUserRequest;
import com.platform.portal.admin.UserAdminDtos.GrantRequest;
import com.platform.portal.admin.UserAdminDtos.RoleDto;
import com.platform.portal.admin.UserAdminDtos.ResetPasswordRequest;
import com.platform.portal.admin.UserAdminDtos.SetEnabledRequest;
import com.platform.portal.admin.UserAdminDtos.UserDto;
import com.platform.security.web.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User administration API (super-admin / org-admin only — enforced in {@link UserAdminService}).
 * The acting principal comes from the verified JWT via {@link CurrentUser}, never a request body.
 */
@RestController
@RequestMapping("/api/portal/admin/users")
public class UserAdminController {

  private final UserAdminService service;

  public UserAdminController(UserAdminService service) {
    this.service = service;
  }

  @GetMapping
  public List<UserDto> list() {
    return service.list(CurrentUser.get());
  }

  @PostMapping
  public UserDto create(@RequestBody CreateUserRequest req) {
    return service.create(req, CurrentUser.get());
  }

  @PutMapping("/{id}/enabled")
  public ResponseEntity<Void> setEnabled(
      @PathVariable UUID id, @RequestBody SetEnabledRequest req) {
    service.setEnabled(id, req.enabled(), CurrentUser.get());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/reset-password")
  public ResponseEntity<Void> resetPassword(
      @PathVariable UUID id, @RequestBody ResetPasswordRequest req) {
    service.resetPassword(id, req.tempPassword(), CurrentUser.get());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/roles")
  public RoleDto grant(@PathVariable UUID id, @RequestBody GrantRequest req) {
    return service.grant(id, req, CurrentUser.get());
  }

  @DeleteMapping("/roles/{grantId}")
  public ResponseEntity<Void> revoke(@PathVariable UUID grantId) {
    service.revoke(grantId, CurrentUser.get());
    return ResponseEntity.noContent().build();
  }
}
