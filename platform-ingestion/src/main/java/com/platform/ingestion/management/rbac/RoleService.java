package com.platform.ingestion.management.rbac;

import com.platform.core.domain.TeamMember;
import com.platform.core.domain.TeamMember.Role;
import com.platform.core.repository.TeamMemberRepository;
import com.platform.core.service.RbacService;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Manages RBAC role assignments ({@code team_members}). Authorization for the grant/revoke
 * operations themselves is enforced here via {@link RbacService}: only ORG_ADMINs manage org-wide
 * roles; TEAM_ADMINs (or ORG_ADMINs) manage their team's roles. A first-admin bootstrap lets the
 * very first ORG_ADMIN be created when none exists yet.
 */
@Service
@Transactional
public class RoleService {

  private static final Logger log = LoggerFactory.getLogger(RoleService.class);

  private final TeamMemberRepository memberRepo;
  private final RbacService rbac;

  public RoleService(TeamMemberRepository memberRepo, RbacService rbac) {
    this.memberRepo = memberRepo;
    this.rbac = rbac;
  }

  @Transactional(readOnly = true)
  public List<TeamMemberDto> list(String scope, UUID scopeId) {
    List<TeamMember> rows =
        "ORG".equalsIgnoreCase(scope)
            ? memberRepo.findByTeamIdIsNull()
            : memberRepo.findByTeamId(required(scopeId, "scopeId"));
    return rows.stream().map(TeamMemberDto::from).toList();
  }

  public TeamMemberDto grant(String actor, GrantRoleRequest req) {
    Role role = parseRole(req.role());
    boolean org = "ORG".equalsIgnoreCase(req.scope());
    UUID teamId = org ? null : required(req.teamId(), "teamId");

    // First-admin bootstrap: allow creating the first ORG_ADMIN with no admins yet.
    boolean bootstrap = role == Role.ORG_ADMIN && !memberRepo.existsByRole(Role.ORG_ADMIN.name());
    if (!bootstrap) {
      boolean allowed = org ? rbac.canManageOrg(actor) : rbac.canManageTeam(actor, teamId);
      if (!allowed) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN, "Actor '" + actor + "' may not grant " + role + " at this scope");
      }
    }

    // Idempotent: return existing identical assignment if present.
    TeamMember existing = findExisting(req.userId(), teamId, role);
    if (existing != null) return TeamMemberDto.from(existing);

    TeamMember saved = memberRepo.save(new TeamMember(req.userId(), teamId, role, actor));
    log.info(
        "[RBAC] {} granted {} to {} (team={}) bootstrap={}",
        actor,
        role,
        req.userId(),
        teamId,
        bootstrap);
    return TeamMemberDto.from(saved);
  }

  public void revoke(String actor, UUID id) {
    TeamMember m =
        memberRepo
            .findById(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Role assignment not found: " + id));

    boolean org = m.getTeamId() == null;
    boolean allowed = org ? rbac.canManageOrg(actor) : rbac.canManageTeam(actor, m.getTeamId());
    if (!allowed) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Actor '" + actor + "' may not revoke this role");
    }
    // Don't strand the org: keep at least one ORG_ADMIN.
    if (Role.ORG_ADMIN.name().equals(m.getRole())
        && memberRepo.countByRole(Role.ORG_ADMIN.name()) <= 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot revoke the last ORG_ADMIN");
    }
    memberRepo.delete(m);
    log.info("[RBAC] {} revoked {} from {}", actor, m.getRole(), m.getUserId());
  }

  private TeamMember findExisting(String userId, UUID teamId, Role role) {
    List<TeamMember> candidates =
        teamId == null
            ? memberRepo.findByUserId(userId).stream().filter(x -> x.getTeamId() == null).toList()
            : memberRepo.findByUserIdAndTeamId(userId, teamId);
    return candidates.stream()
        .filter(x -> role.name().equals(x.getRole()))
        .findFirst()
        .orElse(null);
  }

  private Role parseRole(String role) {
    try {
      return Role.valueOf(role.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role: " + role);
    }
  }

  private static <T> T required(T v, String name) {
    if (v == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " is required");
    return v;
  }
}
