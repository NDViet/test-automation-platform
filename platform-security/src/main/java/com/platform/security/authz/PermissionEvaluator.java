package com.platform.security.authz;

import com.platform.security.jwt.AuthenticatedUser;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * The single authorization decision point. {@code scopeId} is interpreted per the capability's
 * natural scope: project id for VIEW/OPERATE/MANAGE_PROJECT, org id for MANAGE_ORG, ignored for
 * SUPER capabilities (super-admin only). Reused identically by every service.
 */
@Component
public class PermissionEvaluator {

  private final RoleResolver resolver;

  public PermissionEvaluator(RoleResolver resolver) {
    this.resolver = resolver;
  }

  /** True if the user may perform the capability at the given scope. */
  public boolean has(AuthenticatedUser user, Capability capability, UUID scopeId) {
    if (user == null) return false;
    if (user.superAdmin()) return true;
    Tier required = capability.minTier();
    if (required == Tier.SUPER) return false; // only super-admins, handled above

    Tier userTier =
        switch (capability) {
          case MANAGE_ORG -> resolver.tierForOrg(user, scopeId);
          default -> resolver.tierForProject(user, scopeId);
        };
    return userTier != null && userTier.satisfies(required);
  }

  /** Throw 403 unless the user may perform the capability. */
  public void require(AuthenticatedUser user, Capability capability, UUID scopeId) {
    if (!has(user, capability, scopeId)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Not permitted: " + capability + " at scope " + scopeId);
    }
  }
}
