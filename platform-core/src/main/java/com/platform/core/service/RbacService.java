package com.platform.core.service;

import com.platform.core.domain.TeamMember;
import com.platform.core.domain.TeamMember.Role;
import com.platform.core.repository.TeamMemberRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central RBAC decision logic for the platform's four roles (ORG_ADMIN, TEAM_ADMIN, TEAM_MEMBER,
 * VIEWER).
 *
 * <p>This is the authorization core; enforcement points (Spring Security {@code @PreAuthorize},
 * portal route guards) call into it. ORG_ADMIN is granted org-wide (null team); team roles apply to
 * a specific team.
 */
@Service
public class RbacService {

  private final TeamMemberRepository memberRepo;

  public RbacService(TeamMemberRepository memberRepo) {
    this.memberRepo = memberRepo;
  }

  /** True if the user holds ORG_ADMIN anywhere. */
  @Transactional(readOnly = true)
  public boolean isOrgAdmin(String userId) {
    return memberRepo.findByUserId(userId).stream()
        .anyMatch(m -> Role.ORG_ADMIN.name().equals(m.getRole()));
  }

  /** True if the user may manage org-level resources (credentials, teams). */
  @Transactional(readOnly = true)
  public boolean canManageOrg(String userId) {
    return isOrgAdmin(userId);
  }

  /** True if the user may manage a team's projects/integrations/settings. */
  @Transactional(readOnly = true)
  public boolean canManageTeam(String userId, UUID teamId) {
    if (isOrgAdmin(userId)) return true;
    return memberRepo.findByUserIdAndTeamId(userId, teamId).stream()
        .anyMatch(m -> Role.TEAM_ADMIN.name().equals(m.getRole()));
  }

  /** True if the user can read a team's data (any role on the team, or org-wide role). */
  @Transactional(readOnly = true)
  public boolean canViewTeam(String userId, UUID teamId) {
    List<TeamMember> all = memberRepo.findByUserId(userId);
    return all.stream()
        .anyMatch(
            m ->
                m.getTeamId() == null // org-wide ORG_ADMIN / VIEWER
                    || teamId.equals(m.getTeamId()));
  }

  /** True if the user can write to a team's data (TEAM_MEMBER+ on the team, or ORG_ADMIN). */
  @Transactional(readOnly = true)
  public boolean canWriteTeam(String userId, UUID teamId) {
    if (isOrgAdmin(userId)) return true;
    return memberRepo.findByUserIdAndTeamId(userId, teamId).stream()
        .anyMatch(
            m ->
                Role.TEAM_ADMIN.name().equals(m.getRole())
                    || Role.TEAM_MEMBER.name().equals(m.getRole()));
  }
}
