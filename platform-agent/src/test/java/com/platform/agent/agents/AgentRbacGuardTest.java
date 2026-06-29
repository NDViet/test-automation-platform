package com.platform.agent.agents;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.platform.core.domain.Team;
import com.platform.core.repository.TeamRepository;
import com.platform.core.service.RbacService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AgentRbacGuardTest {

  @Mock RbacService rbac;
  @Mock TeamRepository teamRepo;

  AgentRbacGuard guard;

  private final UUID projectId = UUID.randomUUID();
  private final UUID teamId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    guard = new AgentRbacGuard(rbac, teamRepo, true); // enforcement on for these tests
  }

  @Test
  void orgScopeRequiresOrgAdmin() {
    when(rbac.canManageOrg("alice")).thenReturn(true);
    assertThatCode(() -> guard.requireManage("ORG", projectId, "alice")).doesNotThrowAnyException();
  }

  @Test
  void orgScopeDeniedForNonAdmin() {
    when(rbac.canManageOrg("bob")).thenReturn(false);
    assertThatThrownBy(() -> guard.requireManage("ORG", UUID.randomUUID(), "bob"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("may not manage");
  }

  @Test
  void projectScopeAllowedForTeamAdminInProject() {
    when(rbac.isOrgAdmin("carol")).thenReturn(false);
    Team team = new Team(projectId, "QA", "qa");
    when(teamRepo.findByProjectIdOrderByNameAsc(projectId)).thenReturn(List.of(team));
    // team id is null on an un-persisted Team; match whatever id is passed.
    when(rbac.canManageTeam(eq("carol"), any())).thenReturn(true);

    assertThatCode(() -> guard.requireManage("PROJECT", projectId, "carol"))
        .doesNotThrowAnyException();
  }

  @Test
  void projectScopeDeniedWhenNoManageRole() {
    when(rbac.isOrgAdmin("dave")).thenReturn(false);
    when(teamRepo.findByProjectIdOrderByNameAsc(projectId)).thenReturn(List.of());

    assertThatThrownBy(() -> guard.requireManage("PROJECT", projectId, "dave"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("may not manage");
  }

  @Test
  void blankActorDenied() {
    assertThatThrownBy(() -> guard.requireManage("ORG", projectId, " "))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Missing actor");
  }

  @Test
  void disabledIsNoOp() {
    AgentRbacGuard off = new AgentRbacGuard(rbac, teamRepo, false);
    // No role checks happen and nothing throws, regardless of actor.
    assertThatCode(() -> off.requireManage("ORG", projectId, null)).doesNotThrowAnyException();
    org.mockito.Mockito.verifyNoInteractions(rbac, teamRepo);
  }
}
