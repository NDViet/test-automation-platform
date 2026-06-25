package com.platform.core.repository;

import com.platform.core.domain.TeamMember;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {

  List<TeamMember> findByUserId(String userId);

  List<TeamMember> findByUserIdAndTeamId(String userId, UUID teamId);

  List<TeamMember> findByTeamId(UUID teamId);

  /** Org-wide role assignments (team_id IS NULL). */
  List<TeamMember> findByTeamIdIsNull();

  boolean existsByRole(String role);

  long countByRole(String role);
}
