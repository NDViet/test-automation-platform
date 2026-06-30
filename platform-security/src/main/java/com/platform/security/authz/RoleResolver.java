package com.platform.security.authz;

import com.platform.core.domain.UserRole;
import com.platform.core.repository.ProjectRepository;
import com.platform.core.repository.UserRoleRepository;
import com.platform.security.jwt.AuthenticatedUser;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves a user's effective {@link Tier} at a scope from their live {@link UserRole} grants
 * (resolved per request, so revocation is immediate). Org-admins cascade to every project in their
 * org. Super-admins short-circuit to {@link Tier#SUPER}.
 */
@Component
public class RoleResolver {

  private static final String SCOPE_ORG = "ORG";
  private static final String SCOPE_PROJECT = "PROJECT";

  private final UserRoleRepository roleRepo;
  private final ProjectRepository projectRepo;

  public RoleResolver(UserRoleRepository roleRepo, ProjectRepository projectRepo) {
    this.roleRepo = roleRepo;
    this.projectRepo = projectRepo;
  }

  /** Max tier the user holds for a project (incl. their org's ORG_ADMIN cascade); null if none. */
  @Transactional(readOnly = true)
  public Tier tierForProject(AuthenticatedUser user, UUID projectId) {
    if (user.superAdmin()) return Tier.SUPER;
    UUID orgId =
        projectRepo
            .findById(projectId)
            .map(p -> p.getOrganization().getId())
            .orElse(null);
    Tier best = null;
    for (UserRole r : roleRepo.findByUserId(user.userId())) {
      best = max(best, projectTier(r, projectId, orgId));
    }
    return best;
  }

  /** Max tier the user holds for an org (ORG_ADMIN only); null if none. */
  @Transactional(readOnly = true)
  public Tier tierForOrg(AuthenticatedUser user, UUID orgId) {
    if (user.superAdmin()) return Tier.SUPER;
    Tier best = null;
    for (UserRole r : roleRepo.findByUserId(user.userId())) {
      if ("ORG_ADMIN".equals(r.getRole())
          && SCOPE_ORG.equals(r.getScope())
          && orgId.equals(r.getScopeId())) {
        best = max(best, Tier.ADMIN_ORG);
      }
    }
    return best;
  }

  private Tier projectTier(UserRole r, UUID projectId, UUID orgId) {
    if (SCOPE_ORG.equals(r.getScope())) {
      if (orgId == null || !r.getScopeId().equals(orgId)) return null;
      // Org-scoped grants cascade to every project in the org: ORG_ADMIN manages, and an org-wide
      // TESTER/VIEWER (e.g. bootstrap-imported users) operates/reads across all of them.
      return switch (r.getRole()) {
        case "ORG_ADMIN" -> Tier.ADMIN_ORG;
        case "TESTER" -> Tier.OPERATE;
        case "VIEWER" -> Tier.VIEW;
        default -> null;
      };
    }
    if (SCOPE_PROJECT.equals(r.getScope()) && r.getScopeId().equals(projectId)) {
      return switch (r.getRole()) {
        case "PROJECT_ADMIN" -> Tier.ADMIN_PROJECT;
        case "TESTER" -> Tier.OPERATE;
        case "VIEWER" -> Tier.VIEW;
        default -> null;
      };
    }
    return null;
  }

  private static Tier max(Tier a, Tier b) {
    if (a == null) return b;
    if (b == null) return a;
    return a.ordinal() >= b.ordinal() ? a : b;
  }
}
