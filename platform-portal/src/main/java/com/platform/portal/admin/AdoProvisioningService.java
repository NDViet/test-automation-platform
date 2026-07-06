package com.platform.portal.admin;

import com.platform.core.domain.AdoUser;
import com.platform.core.domain.User;
import com.platform.core.domain.UserRole;
import com.platform.core.repository.AdoUserRepository;
import com.platform.core.repository.UserRepository;
import com.platform.core.repository.UserRoleRepository;
import com.platform.security.jwt.AuthenticatedUser;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Provisions a project's ADO people (everyone in its ADO Users directory) as platform users so they
 * become assignable in the member picker and can log in to work their queue.
 *
 * <p>For each ADO person with an email, finds-or-creates a {@link User} (linked by email) and
 * ensures a PROJECT {@code TESTER} role for the project. Created users are <b>enabled</b> — so they
 * are immediately assignable — but <b>login-locked</b>: their password is a high-entropy random
 * value nobody knows, with {@code mustChangePassword=true}, so they cannot authenticate until an
 * admin resets their password (the existing invite/reset flow). This avoids minting a pile of
 * known/dormant credentials while still populating the assignable directory.
 *
 * <p>Idempotent: re-running links existing users and adds only missing grants; it never duplicates
 * users or grants, and never disables/deletes people who left ADO. Admin-only (super or org-admin),
 * mirroring {@link UserAdminService}.
 */
@Service
public class AdoProvisioningService {

  private static final Logger log = LoggerFactory.getLogger(AdoProvisioningService.class);

  private static final String SCOPE_ORG = "ORG";
  private static final String SCOPE_PROJECT = "PROJECT";
  private static final String ROLE_TESTER = "TESTER";

  private final UserRepository users;
  private final UserRoleRepository roles;
  private final PasswordEncoder encoder;
  private final AdoUserRepository adoUsers;

  public AdoProvisioningService(
      UserRepository users,
      UserRoleRepository roles,
      PasswordEncoder encoder,
      AdoUserRepository adoUsers) {
    this.users = users;
    this.roles = roles;
    this.encoder = encoder;
    this.adoUsers = adoUsers;
  }

  /** Outcome of a provisioning run — counts only, never any credential material. */
  public record ProvisionSummary(
      int total,
      int created,
      int linked,
      int activated,
      int granted,
      int alreadyMembers,
      int skippedNoEmail) {}

  @Transactional
  public ProvisionSummary provisionProject(UUID projectId, AuthenticatedUser actor) {
    requireUserAdmin(actor);

    int total = 0;
    int created = 0;
    int linked = 0;
    int activated = 0;
    int granted = 0;
    int alreadyMembers = 0;
    int skippedNoEmail = 0;

    // Everyone shown in the project's ADO Users tab (findByProjectIdOrderByDisplayName) is
    // provisioned — team members and work-item participants alike — so the assignable directory
    // matches what the admin sees. Membership is not a gate; only a usable email is required (it is
    // the idempotency/link key), so people without one are reported skipped.
    for (AdoUser ado : adoUsers.findByProjectIdOrderByDisplayName(projectId)) {
      String email = ado.getEmail();
      if (isBlank(email)) {
        skippedNoEmail++;
        continue;
      }
      total++;

      User user = users.findByEmailIgnoreCase(email.trim()).orElse(null);
      if (user == null) {
        user = users.save(newLoginLockedUser(ado, email.trim()));
        created++;
      } else {
        linked++;
        // ADO shadow accounts are created disabled + login-locked by the ADO bootstrap. Activating
        // them here (without touching their password) is what makes the person assignable — the
        // whole point of provisioning. Login stays locked until an admin resets the password.
        if (!user.isEnabled()) {
          user.setEnabled(true);
          users.save(user);
          activated++;
        }
      }

      if (ensureTesterGrant(user.getId(), projectId, actor.username())) {
        granted++;
      } else {
        alreadyMembers++;
      }
    }

    // Auditable trace of who provisioned what — counts only, never any credential material.
    log.info(
        "ADO user provisioning: project={} actor={} total={} created={} linked={} activated={}"
            + " granted={} alreadyMembers={} skippedNoEmail={}",
        projectId,
        actor.username(),
        total,
        created,
        linked,
        activated,
        granted,
        alreadyMembers,
        skippedNoEmail);
    return new ProvisionSummary(
        total, created, linked, activated, granted, alreadyMembers, skippedNoEmail);
  }

  /** A new enabled-but-login-locked user for an ADO member (unknown random password). */
  private User newLoginLockedUser(AdoUser ado, String email) {
    String displayName =
        notBlank(ado.getDisplayName()) ? ado.getDisplayName() : ado.getUniqueName();
    String username = uniqueUsername(email, ado.getUniqueName());
    // High-entropy secret nobody holds — the account can only be logged into after an admin reset.
    String unknownSecret = UUID.randomUUID().toString() + UUID.randomUUID();
    return new User(username, email, encoder.encode(unknownSecret), displayName, false, true);
  }

  /** Ensure a PROJECT TESTER grant exists; returns true if a new grant was created. */
  private boolean ensureTesterGrant(UUID userId, UUID projectId, String actorUsername) {
    boolean present =
        roles
            .findByUserIdAndRoleAndScopeAndScopeId(userId, ROLE_TESTER, SCOPE_PROJECT, projectId)
            .isPresent();
    if (present) {
      return false;
    }
    roles.save(new UserRole(userId, ROLE_TESTER, SCOPE_PROJECT, projectId, actorUsername));
    return true;
  }

  /** Derive a unique username from the email local-part (fallback: ADO unique name). */
  private String uniqueUsername(String email, String uniqueName) {
    String base = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
    base = base.trim().toLowerCase();
    if (base.isEmpty()) {
      base = notBlank(uniqueName) ? uniqueName.trim().toLowerCase() : "user";
    }
    if (!users.existsByUsername(base)) {
      return base;
    }
    for (int i = 2; i < 1000; i++) {
      String candidate = base + "-" + i;
      if (!users.existsByUsername(candidate)) {
        return candidate;
      }
    }
    return base + "-" + UUID.randomUUID();
  }

  /** Only super-admins or org-admins may provision users (mirrors UserAdminService). */
  private void requireUserAdmin(AuthenticatedUser actor) {
    if (actor == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
    }
    if (actor.superAdmin()) {
      return;
    }
    boolean orgAdmin =
        roles.findByUserId(actor.userId()).stream()
            .anyMatch(r -> "ORG_ADMIN".equals(r.getRole()) && SCOPE_ORG.equals(r.getScope()));
    if (!orgAdmin) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Provisioning users requires org-admin");
    }
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }
}
