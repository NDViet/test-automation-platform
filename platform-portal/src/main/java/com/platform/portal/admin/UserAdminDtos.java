package com.platform.portal.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response shapes for the user-administration API ({@code /api/portal/admin/users}). */
public final class UserAdminDtos {

  private UserAdminDtos() {}

  public record RoleDto(UUID id, String role, String scope, UUID scopeId) {}

  public record UserDto(
      UUID id,
      String username,
      String displayName,
      String email,
      boolean superAdmin,
      boolean enabled,
      boolean mustChangePassword,
      Instant lastLoginAt,
      List<RoleDto> roles) {}

  public record CreateUserRequest(
      String username, String displayName, String email, String tempPassword) {}

  public record SetEnabledRequest(boolean enabled) {}

  public record ResetPasswordRequest(String tempPassword) {}

  public record GrantRequest(String role, String scope, UUID scopeId) {}
}
