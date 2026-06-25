package com.platform.ingestion.management.rbac;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.platform.core.domain.TeamMember;
import com.platform.core.domain.TeamMember.Role;
import com.platform.core.repository.TeamMemberRepository;
import com.platform.core.service.RbacService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class RoleServiceTest {

  private TeamMemberRepository repo;
  private RbacService rbac;
  private RoleService service;
  private final UUID teamId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    repo = mock(TeamMemberRepository.class);
    rbac = mock(RbacService.class);
    service = new RoleService(repo, rbac);
    when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    when(repo.findByUserId(any())).thenReturn(List.of());
    when(repo.findByUserIdAndTeamId(any(), any())).thenReturn(List.of());
  }

  @Test
  void bootstrap_allowsFirstOrgAdmin_withoutPermission() {
    when(repo.existsByRole("ORG_ADMIN")).thenReturn(false);

    TeamMemberDto dto =
        service.grant("anyone", new GrantRoleRequest("alice", "ORG", null, "ORG_ADMIN"));

    assertThat(dto.role()).isEqualTo("ORG_ADMIN");
    verify(repo).save(any());
    verify(rbac, never()).canManageOrg(anyString()); // bootstrap skips the check
  }

  @Test
  void nonAdmin_cannotGrantOrgRole() {
    when(repo.existsByRole("ORG_ADMIN")).thenReturn(true);
    when(rbac.canManageOrg("bob")).thenReturn(false);

    assertThatThrownBy(
            () -> service.grant("bob", new GrantRoleRequest("carol", "ORG", null, "VIEWER")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("403");
  }

  @Test
  void teamAdmin_canGrantTeamRole() {
    when(repo.existsByRole("ORG_ADMIN")).thenReturn(true);
    when(rbac.canManageTeam("lead", teamId)).thenReturn(true);

    TeamMemberDto dto =
        service.grant("lead", new GrantRoleRequest("dev", "TEAM", teamId, "TEAM_MEMBER"));

    assertThat(dto.role()).isEqualTo("TEAM_MEMBER");
    assertThat(dto.teamId()).isEqualTo(teamId.toString());
  }

  @Test
  void revoke_lastOrgAdmin_isBlocked() {
    UUID id = UUID.randomUUID();
    TeamMember m = new TeamMember("alice", null, Role.ORG_ADMIN, "system");
    when(repo.findById(id)).thenReturn(Optional.of(m));
    when(rbac.canManageOrg("admin")).thenReturn(true);
    when(repo.countByRole("ORG_ADMIN")).thenReturn(1L);

    assertThatThrownBy(() -> service.revoke("admin", id))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("last ORG_ADMIN");
    verify(repo, never()).delete(any());
  }
}
