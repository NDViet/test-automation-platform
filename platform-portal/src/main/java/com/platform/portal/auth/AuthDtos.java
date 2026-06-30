package com.platform.portal.auth;

import java.util.List;

/** Request/response payloads for authentication. Passwords are never echoed back. */
public final class AuthDtos {

  private AuthDtos() {}

  public record LoginRequest(String username, String password) {}

  public record ChangePasswordRequest(String currentPassword, String newPassword) {}

  /** A role grant the user holds. */
  public record RoleGrant(String role, String scope, String scopeId) {}

  /** Current-user view returned by login / me / change-password. */
  public record MeResponse(
      String username,
      String displayName,
      boolean superAdmin,
      boolean mustChangePassword,
      List<RoleGrant> roles) {}
}
