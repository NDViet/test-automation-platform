package com.platform.portal.admin;

import com.platform.core.domain.User;
import com.platform.core.domain.UserRole;
import com.platform.core.repository.UserRepository;
import com.platform.core.repository.UserRoleRepository;
import com.platform.portal.admin.UserAdminDtos.CreateUserRequest;
import com.platform.portal.admin.UserAdminDtos.GrantRequest;
import com.platform.portal.admin.UserAdminDtos.RoleDto;
import com.platform.portal.admin.UserAdminDtos.UserDto;
import com.platform.security.authz.RoleResolver;
import com.platform.security.authz.Tier;
import com.platform.security.jwt.AuthenticatedUser;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * User administration backing {@code /api/portal/admin/users}: list/create users, enable/disable,
 * reset password, and grant/revoke {@link UserRole} grants. Authorization is enforced here (the
 * portal owns auth): only super-admins and org-admins may manage users, a grantor can never grant a
 * role above their own tier at the target scope, and the last super-admin / last org-admin of an
 * org is protected from removal.
 */
@Service
public class UserAdminService {

  private static final String SCOPE_ORG = "ORG";
  private static final String SCOPE_PROJECT = "PROJECT";
  private static final Set<String> PROJECT_ROLES = Set.of("PROJECT_ADMIN", "TESTER", "VIEWER");

  private final UserRepository users;
  private final UserRoleRepository roles;
  private final PasswordEncoder encoder;
  private final RoleResolver roleResolver;

  public UserAdminService(
      UserRepository users,
      UserRoleRepository roles,
      PasswordEncoder encoder,
      RoleResolver roleResolver) {
    this.users = users;
    this.roles = roles;
    this.encoder = encoder;
    this.roleResolver = roleResolver;
  }

  @Transactional(readOnly = true)
  public List<UserDto> list(AuthenticatedUser actor) {
    requireUserAdmin(actor);
    return users.findAllByOrderByUsernameAsc().stream().map(this::toDto).toList();
  }

  @Transactional
  public UserDto create(CreateUserRequest req, AuthenticatedUser actor) {
    requireUserAdmin(actor);
    if (req == null || isBlank(req.username()) || isBlank(req.tempPassword())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "username and tempPassword are required");
    }
    String username = req.username().trim();
    if (users.existsByUsername(username)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Username already exists: " + username);
    }
    // Admin-created users are normal users (never super) and must change the temp password.
    User user =
        new User(
            username,
            blankToNull(req.email()),
            encoder.encode(req.tempPassword()),
            blankToNull(req.displayName()),
            false,
            true);
    return toDto(users.save(user));
  }

  @Transactional
  public void setEnabled(UUID userId, boolean enabled, AuthenticatedUser actor) {
    requireUserAdmin(actor);
    User user = users.findById(userId).orElseThrow(this::notFound);
    if (!enabled && user.isSuperAdmin() && isLastEnabledSuperAdmin(user)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Cannot disable the last enabled super-admin");
    }
    user.setEnabled(enabled);
    users.save(user);
  }

  @Transactional
  public void resetPassword(UUID userId, String tempPassword, AuthenticatedUser actor) {
    requireUserAdmin(actor);
    if (isBlank(tempPassword)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tempPassword is required");
    }
    User user = users.findById(userId).orElseThrow(this::notFound);
    user.resetPasswordByAdmin(encoder.encode(tempPassword)); // forces a change on next login
    users.save(user);
  }

  @Transactional
  public RoleDto grant(UUID userId, GrantRequest req, AuthenticatedUser actor) {
    requireUserAdmin(actor);
    User user = users.findById(userId).orElseThrow(this::notFound);
    validateRoleScope(req);
    requireGrantorOutranks(actor, req.role(), req.scope(), req.scopeId());

    UserRole existing =
        roles
            .findByUserIdAndRoleAndScopeAndScopeId(
                user.getId(), req.role(), req.scope(), req.scopeId())
            .orElse(null);
    if (existing != null) {
      return toRoleDto(existing); // idempotent
    }
    UserRole saved =
        roles.save(
            new UserRole(user.getId(), req.role(), req.scope(), req.scopeId(), actor.username()));
    return toRoleDto(saved);
  }

  @Transactional
  public void revoke(UUID grantId, AuthenticatedUser actor) {
    requireUserAdmin(actor);
    UserRole grant = roles.findById(grantId).orElseThrow(this::notFound);
    requireGrantorOutranks(actor, grant.getRole(), grant.getScope(), grant.getScopeId());
    if ("ORG_ADMIN".equals(grant.getRole()) && isLastOrgAdmin(grant.getScopeId())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Cannot revoke the last org-admin of the organization");
    }
    roles.delete(grant);
  }

  // ── Authorization ─────────────────────────────────────────────────────────

  /** Only super-admins or org-admins (of any org) may administer users. */
  private void requireUserAdmin(AuthenticatedUser actor) {
    if (actor == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
    }
    if (actor.superAdmin()) return;
    boolean orgAdmin =
        roles.findByUserId(actor.userId()).stream()
            .anyMatch(r -> "ORG_ADMIN".equals(r.getRole()) && SCOPE_ORG.equals(r.getScope()));
    if (!orgAdmin) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "User administration requires org-admin");
    }
  }

  /** The grantor's effective tier at the target scope must meet/exceed the granted role's tier. */
  private void requireGrantorOutranks(
      AuthenticatedUser actor, String role, String scope, UUID scopeId) {
    Tier required = roleTier(role);
    Tier actorTier =
        SCOPE_ORG.equals(scope)
            ? roleResolver.tierForOrg(actor, scopeId)
            : roleResolver.tierForProject(actor, scopeId);
    if (actorTier == null || !actorTier.satisfies(required)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "You cannot grant or revoke a role above your own at this scope");
    }
  }

  private void validateRoleScope(GrantRequest req) {
    if (req == null || isBlank(req.role()) || isBlank(req.scope()) || req.scopeId() == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "role, scope and scopeId are required");
    }
    boolean ok =
        ("ORG_ADMIN".equals(req.role()) && SCOPE_ORG.equals(req.scope()))
            || (PROJECT_ROLES.contains(req.role()) && SCOPE_PROJECT.equals(req.scope()));
    if (!ok) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Invalid role/scope combination: " + req.role() + "/" + req.scope());
    }
  }

  private static Tier roleTier(String role) {
    return switch (role) {
      case "VIEWER" -> Tier.VIEW;
      case "TESTER" -> Tier.OPERATE;
      case "PROJECT_ADMIN" -> Tier.ADMIN_PROJECT;
      case "ORG_ADMIN" -> Tier.ADMIN_ORG;
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role: " + role);
    };
  }

  private boolean isLastEnabledSuperAdmin(User candidate) {
    return users.findAllByOrderByUsernameAsc().stream()
        .filter(u -> u.isSuperAdmin() && u.isEnabled() && !u.getId().equals(candidate.getId()))
        .findAny()
        .isEmpty();
  }

  private boolean isLastOrgAdmin(UUID orgId) {
    long count =
        roles.findByScopeAndScopeId(SCOPE_ORG, orgId).stream()
            .filter(r -> "ORG_ADMIN".equals(r.getRole()))
            .count();
    return count <= 1;
  }

  // ── Mapping / helpers ───────────────────────────────────────────────────────

  private UserDto toDto(User u) {
    List<RoleDto> grants = roles.findByUserId(u.getId()).stream().map(this::toRoleDto).toList();
    return new UserDto(
        u.getId(),
        u.getUsername(),
        u.getDisplayName(),
        u.getEmail(),
        u.isSuperAdmin(),
        u.isEnabled(),
        u.isMustChangePassword(),
        u.getLastLoginAt(),
        grants);
  }

  private RoleDto toRoleDto(UserRole r) {
    return new RoleDto(r.getId(), r.getRole(), r.getScope(), r.getScopeId());
  }

  private ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "User or grant not found");
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private static String blankToNull(String s) {
    return isBlank(s) ? null : s.trim();
  }
}
