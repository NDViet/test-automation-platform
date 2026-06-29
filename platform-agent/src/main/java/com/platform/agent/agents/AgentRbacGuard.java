package com.platform.agent.agents;

import com.platform.core.repository.TeamRepository;
import com.platform.core.service.RbacService;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Authorization for agent/task-agent writes. Mirrors the platform's role model (see {@link
 * RbacService}): ORG-scoped resources require ORG_ADMIN; PROJECT-scoped resources require ORG_ADMIN
 * or TEAM_ADMIN of a team within that project. Reads are not gated here.
 *
 * <p>The {@code actor} is the X-Actor user id forwarded by the portal — the same identity {@code
 * RoleService} authorizes against.
 *
 * <p><b>On hold:</b> enforcement is gated by {@code agent.rbac.enabled} (default {@code false}).
 * Enforcing now would lock out agent management on a platform that hasn't set up RBAC. Flip the
 * flag on once platform-wide RBAC is in place — the logic and tests are ready.
 */
@Component
public class AgentRbacGuard {

  static final String SCOPE_ORG = "ORG";

  private final RbacService rbac;
  private final TeamRepository teamRepo;
  private final boolean enabled;

  public AgentRbacGuard(
      RbacService rbac,
      TeamRepository teamRepo,
      @Value("${agent.rbac.enabled:false}") boolean enabled) {
    this.rbac = rbac;
    this.teamRepo = teamRepo;
    this.enabled = enabled;
  }

  /**
   * Throw 403 unless {@code actor} may manage resources at the given scope (no-op when disabled).
   */
  public void requireManage(String scope, UUID scopeId, String actor) {
    if (!enabled) {
      return; // RBAC for agent management is on hold platform-wide
    }
    if (actor == null || actor.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Missing actor — cannot authorize agent management");
    }
    boolean allowed =
        SCOPE_ORG.equals(scope) ? rbac.canManageOrg(actor) : canManageProject(actor, scopeId);
    if (!allowed) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Actor '" + actor + "' may not manage agents at " + scope + " scope");
    }
  }

  /** ORG_ADMIN, or TEAM_ADMIN of any team in the project. */
  private boolean canManageProject(String actor, UUID projectId) {
    if (rbac.isOrgAdmin(actor)) {
      return true;
    }
    return teamRepo.findByProjectIdOrderByNameAsc(projectId).stream()
        .anyMatch(team -> rbac.canManageTeam(actor, team.getId()));
  }
}
