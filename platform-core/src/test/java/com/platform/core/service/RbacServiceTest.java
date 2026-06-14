package com.platform.core.service;

import com.platform.core.domain.TeamMember;
import com.platform.core.domain.TeamMember.Role;
import com.platform.core.repository.TeamMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RbacServiceTest {

    private TeamMemberRepository repo;
    private RbacService rbac;

    private final UUID teamA = UUID.randomUUID();
    private final UUID teamB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repo = mock(TeamMemberRepository.class);
        rbac = new RbacService(repo);
    }

    @Test
    void orgAdmin_canManageAnyTeamAndOrg() {
        when(repo.findByUserId("admin")).thenReturn(List.of(
                new TeamMember("admin", null, Role.ORG_ADMIN, "system")));

        assertThat(rbac.isOrgAdmin("admin")).isTrue();
        assertThat(rbac.canManageOrg("admin")).isTrue();
        assertThat(rbac.canManageTeam("admin", teamA)).isTrue();
        assertThat(rbac.canViewTeam("admin", teamB)).isTrue();
    }

    @Test
    void teamAdmin_managesOwnTeamOnly() {
        when(repo.findByUserId("lead")).thenReturn(List.of(
                new TeamMember("lead", teamA, Role.TEAM_ADMIN, "admin")));
        when(repo.findByUserIdAndTeamId("lead", teamA)).thenReturn(List.of(
                new TeamMember("lead", teamA, Role.TEAM_ADMIN, "admin")));
        when(repo.findByUserIdAndTeamId("lead", teamB)).thenReturn(List.of());

        assertThat(rbac.isOrgAdmin("lead")).isFalse();
        assertThat(rbac.canManageTeam("lead", teamA)).isTrue();
        assertThat(rbac.canManageTeam("lead", teamB)).isFalse();
        assertThat(rbac.canViewTeam("lead", teamA)).isTrue();
    }

    @Test
    void teamMember_canWriteButNotManage() {
        when(repo.findByUserId("dev")).thenReturn(List.of(
                new TeamMember("dev", teamA, Role.TEAM_MEMBER, "lead")));
        when(repo.findByUserIdAndTeamId("dev", teamA)).thenReturn(List.of(
                new TeamMember("dev", teamA, Role.TEAM_MEMBER, "lead")));

        assertThat(rbac.canWriteTeam("dev", teamA)).isTrue();
        assertThat(rbac.canManageTeam("dev", teamA)).isFalse();
    }

    @Test
    void unknownUser_hasNoAccess() {
        when(repo.findByUserId("stranger")).thenReturn(List.of());
        when(repo.findByUserIdAndTeamId("stranger", teamA)).thenReturn(List.of());

        assertThat(rbac.canViewTeam("stranger", teamA)).isFalse();
        assertThat(rbac.canWriteTeam("stranger", teamA)).isFalse();
        assertThat(rbac.isOrgAdmin("stranger")).isFalse();
    }
}
