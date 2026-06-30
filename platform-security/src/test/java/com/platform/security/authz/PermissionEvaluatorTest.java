package com.platform.security.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.platform.core.domain.Organization;
import com.platform.core.domain.Project;
import com.platform.core.domain.UserRole;
import com.platform.core.repository.ProjectRepository;
import com.platform.core.repository.UserRoleRepository;
import com.platform.security.jwt.AuthenticatedUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PermissionEvaluatorTest {

  @Mock UserRoleRepository roleRepo;
  @Mock ProjectRepository projectRepo;

  PermissionEvaluator evaluator;

  private final UUID projectId = UUID.randomUUID();
  private final UUID otherProjectId = UUID.randomUUID();
  private final UUID orgId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    evaluator = new PermissionEvaluator(new RoleResolver(roleRepo, projectRepo));
    Project project = mock(Project.class);
    Organization org = mock(Organization.class);
    lenient().when(org.getId()).thenReturn(orgId);
    lenient().when(project.getOrganization()).thenReturn(org);
    lenient().when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));
  }

  private AuthenticatedUser user() {
    return new AuthenticatedUser(userId, "u", false);
  }

  private void grant(String role, String scope, UUID scopeId) {
    when(roleRepo.findByUserId(userId))
        .thenReturn(List.of(new UserRole(userId, role, scope, scopeId, "admin")));
  }

  @Test
  void superAdminAllowedEverything() {
    AuthenticatedUser su = new AuthenticatedUser(userId, "s", true);
    assertThat(evaluator.has(su, Capability.MANAGE_PLATFORM, null)).isTrue();
    assertThat(evaluator.has(su, Capability.MANAGE_AI_GATEWAY, null)).isTrue();
    assertThat(evaluator.has(su, Capability.IMPORT_ADO_STRUCTURE, null)).isTrue();
    assertThat(evaluator.has(su, Capability.OPERATE_QUALITY, projectId)).isTrue();
  }

  @Test
  void viewerReadOnly() {
    grant("VIEWER", "PROJECT", projectId);
    assertThat(evaluator.has(user(), Capability.VIEW_RESULTS, projectId)).isTrue();
    assertThat(evaluator.has(user(), Capability.OPERATE_QUALITY, projectId)).isFalse();
    assertThat(evaluator.has(user(), Capability.MANAGE_PROJECT, projectId)).isFalse();
  }

  @Test
  void testerCanOperateNotConfigure() {
    grant("TESTER", "PROJECT", projectId);
    assertThat(evaluator.has(user(), Capability.OPERATE_QUALITY, projectId))
        .isTrue(); // incl. agents
    assertThat(evaluator.has(user(), Capability.VIEW_RESULTS, projectId)).isTrue();
    assertThat(evaluator.has(user(), Capability.MANAGE_PROJECT, projectId)).isFalse();
  }

  @Test
  void projectAdminManagesProjectNotOrg() {
    grant("PROJECT_ADMIN", "PROJECT", projectId);
    assertThat(evaluator.has(user(), Capability.MANAGE_PROJECT, projectId)).isTrue();
    assertThat(evaluator.has(user(), Capability.OPERATE_QUALITY, projectId)).isTrue();
    assertThat(evaluator.has(user(), Capability.MANAGE_ORG, orgId)).isFalse();
  }

  @Test
  void orgAdminManagesOrgAndCascadesToProjectsButNotSuper() {
    grant("ORG_ADMIN", "ORG", orgId);
    assertThat(evaluator.has(user(), Capability.MANAGE_ORG, orgId)).isTrue();
    assertThat(evaluator.has(user(), Capability.MANAGE_PROJECT, projectId)).isTrue(); // cascade
    assertThat(evaluator.has(user(), Capability.OPERATE_QUALITY, projectId)).isTrue();
    assertThat(evaluator.has(user(), Capability.MANAGE_AI_GATEWAY, null)).isFalse(); // SUPER only
    assertThat(evaluator.has(user(), Capability.IMPORT_ADO_STRUCTURE, null)).isFalse();
  }

  @Test
  void testerIsolatedToTheirProject() {
    grant("TESTER", "PROJECT", projectId);
    lenient().when(projectRepo.findById(otherProjectId)).thenReturn(Optional.empty());
    assertThat(evaluator.has(user(), Capability.OPERATE_QUALITY, otherProjectId)).isFalse();
  }

  @Test
  void orgScopedViewerSeesEveryProjectReadOnly() {
    // Org-wide viewer (e.g. bootstrap-imported ADO users): VIEW on every project in the org.
    grant("VIEWER", "ORG", orgId);
    assertThat(evaluator.has(user(), Capability.VIEW_RESULTS, projectId)).isTrue();
    assertThat(evaluator.has(user(), Capability.OPERATE_QUALITY, projectId)).isFalse();
    assertThat(evaluator.has(user(), Capability.MANAGE_PROJECT, projectId)).isFalse();
  }

  @Test
  void orgScopedTesterOperatesEveryProject() {
    grant("TESTER", "ORG", orgId);
    assertThat(evaluator.has(user(), Capability.OPERATE_QUALITY, projectId)).isTrue();
    assertThat(evaluator.has(user(), Capability.VIEW_RESULTS, projectId)).isTrue();
    assertThat(evaluator.has(user(), Capability.MANAGE_PROJECT, projectId)).isFalse();
  }

  @Test
  void orgScopedViewerDoesNotLeakToOtherOrg() {
    grant("VIEWER", "ORG", orgId);
    Project other = mock(Project.class);
    Organization otherOrg = mock(Organization.class);
    lenient().when(otherOrg.getId()).thenReturn(UUID.randomUUID()); // different org
    lenient().when(other.getOrganization()).thenReturn(otherOrg);
    lenient().when(projectRepo.findById(otherProjectId)).thenReturn(Optional.of(other));
    assertThat(evaluator.has(user(), Capability.VIEW_RESULTS, otherProjectId)).isFalse();
  }
}
