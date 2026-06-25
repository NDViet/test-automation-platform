package com.platform.ingestion.management.rbac;

import com.platform.core.domain.TeamMember;

public record TeamMemberDto(
    String id,
    String userId,
    String teamId, // null = org-wide role
    String role,
    String grantedBy,
    String grantedAt) {
  public static TeamMemberDto from(TeamMember m) {
    return new TeamMemberDto(
        m.getId() != null ? m.getId().toString() : null,
        m.getUserId(),
        m.getTeamId() != null ? m.getTeamId().toString() : null,
        m.getRole(),
        m.getGrantedBy(),
        m.getGrantedAt() != null ? m.getGrantedAt().toString() : null);
  }
}
