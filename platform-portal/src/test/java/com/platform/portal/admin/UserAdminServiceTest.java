package com.platform.portal.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.core.domain.User;
import com.platform.core.domain.UserRole;
import com.platform.core.repository.UserRepository;
import com.platform.core.repository.UserRoleRepository;
import com.platform.portal.admin.UserAdminDtos.CreateUserRequest;
import com.platform.portal.admin.UserAdminDtos.GrantRequest;
import com.platform.security.authz.RoleResolver;
import com.platform.security.authz.Tier;
import com.platform.security.jwt.AuthenticatedUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

  @Mock UserRepository users;
  @Mock UserRoleRepository roles;
  @Mock PasswordEncoder encoder;
  @Mock RoleResolver roleResolver;

  UserAdminService service;

  private final AuthenticatedUser superActor =
      new AuthenticatedUser(UUID.randomUUID(), "root", true);

  @BeforeEach
  void setUp() {
    service = new UserAdminService(users, roles, encoder, roleResolver);
  }

  // ── create ──────────────────────────────────────────────────────────────

  @Test
  void createPersistsHashedUserForcedToChange() {
    when(users.existsByUsername("bob")).thenReturn(false);
    when(encoder.encode("temp123")).thenReturn("HASH");
    when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    service.create(new CreateUserRequest("bob", "Bob", "bob@x.io", "temp123"), superActor);

    ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
    verify(users).save(cap.capture());
    User saved = cap.getValue();
    assertThat(saved.getUsername()).isEqualTo("bob");
    assertThat(saved.getPasswordHash()).isEqualTo("HASH");
    assertThat(saved.isMustChangePassword()).isTrue();
    assertThat(saved.isSuperAdmin()).isFalse();
  }

  @Test
  void createRejectsDuplicateUsername() {
    when(users.existsByUsername("bob")).thenReturn(true);
    assertThatThrownBy(
            () -> service.create(new CreateUserRequest("bob", "Bob", null, "temp123"), superActor))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("already exists");
  }

  @Test
  void nonAdminActorDenied() {
    AuthenticatedUser tester = new AuthenticatedUser(UUID.randomUUID(), "tester1", false);
    when(roles.findByUserId(tester.userId())).thenReturn(List.of()); // no ORG_ADMIN grants
    assertThatThrownBy(
            () -> service.create(new CreateUserRequest("x", null, null, "p"), tester))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("org-admin");
  }

  // ── grant ───────────────────────────────────────────────────────────────

  @Test
  void grantOrgAdminAllowedWhenActorOutranks() {
    UUID userId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    User target = mockUser(userId);
    when(users.findById(userId)).thenReturn(Optional.of(target));
    when(roleResolver.tierForOrg(superActor, orgId)).thenReturn(Tier.SUPER);
    when(roles.findByUserIdAndRoleAndScopeAndScopeId(userId, "ORG_ADMIN", "ORG", orgId))
        .thenReturn(Optional.empty());
    when(roles.save(any(UserRole.class))).thenAnswer(inv -> inv.getArgument(0));

    var dto = service.grant(userId, new GrantRequest("ORG_ADMIN", "ORG", orgId), superActor);

    assertThat(dto.role()).isEqualTo("ORG_ADMIN");
    verify(roles).save(any(UserRole.class));
  }

  @Test
  void grantDeniedWhenActorDoesNotOutrankAtScope() {
    AuthenticatedUser orgAdminOfOther =
        new AuthenticatedUser(UUID.randomUUID(), "oa", false);
    UUID userId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    // requireUserAdmin passes: actor holds an ORG_ADMIN grant somewhere…
    when(roles.findByUserId(orgAdminOfOther.userId()))
        .thenReturn(List.of(new UserRole(orgAdminOfOther.userId(), "ORG_ADMIN", "ORG", UUID.randomUUID(), "x")));
    User target = mockUser(userId);
    when(users.findById(userId)).thenReturn(Optional.of(target));
    // …but NOT for this target org.
    when(roleResolver.tierForOrg(orgAdminOfOther, orgId)).thenReturn(null);

    assertThatThrownBy(
            () -> service.grant(userId, new GrantRequest("ORG_ADMIN", "ORG", orgId), orgAdminOfOther))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("above your own");
    verify(roles, never()).save(any());
  }

  @Test
  void grantRejectsInvalidRoleScopeCombo() {
    UUID userId = UUID.randomUUID();
    User target = mockUser(userId);
    when(users.findById(userId)).thenReturn(Optional.of(target));
    assertThatThrownBy(
            () -> service.grant(userId, new GrantRequest("ORG_ADMIN", "PROJECT", UUID.randomUUID()), superActor))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Invalid role/scope");
  }

  // ── protections ───────────────────────────────────────────────────────────

  @Test
  void cannotDisableLastEnabledSuperAdmin() {
    UUID id = UUID.randomUUID();
    User onlySuper = mockUser(id);
    when(onlySuper.isSuperAdmin()).thenReturn(true);
    when(onlySuper.isEnabled()).thenReturn(true);
    when(users.findById(id)).thenReturn(Optional.of(onlySuper));
    when(users.findAllByOrderByUsernameAsc()).thenReturn(List.of(onlySuper));

    assertThatThrownBy(() -> service.setEnabled(id, false, superActor))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("last enabled super-admin");
  }

  @Test
  void cannotRevokeLastOrgAdmin() {
    UUID grantId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UserRole grant = new UserRole(UUID.randomUUID(), "ORG_ADMIN", "ORG", orgId, "x");
    when(roles.findById(grantId)).thenReturn(Optional.of(grant));
    when(roleResolver.tierForOrg(superActor, orgId)).thenReturn(Tier.SUPER);
    when(roles.findByScopeAndScopeId("ORG", orgId)).thenReturn(List.of(grant)); // only one

    assertThatThrownBy(() -> service.revoke(grantId, superActor))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("last org-admin");
    verify(roles, never()).delete(any());
  }

  @Test
  void revokeSucceedsWhenAnotherOrgAdminRemains() {
    UUID grantId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UserRole grant = new UserRole(UUID.randomUUID(), "ORG_ADMIN", "ORG", orgId, "x");
    UserRole other = new UserRole(UUID.randomUUID(), "ORG_ADMIN", "ORG", orgId, "y");
    when(roles.findById(grantId)).thenReturn(Optional.of(grant));
    when(roleResolver.tierForOrg(superActor, orgId)).thenReturn(Tier.SUPER);
    when(roles.findByScopeAndScopeId("ORG", orgId)).thenReturn(List.of(grant, other));

    service.revoke(grantId, superActor);

    verify(roles).delete(grant);
  }

  // ── helpers ─────────────────────────────────────────────────────────────

  private static User mockUser(UUID id) {
    User u = org.mockito.Mockito.mock(User.class);
    org.mockito.Mockito.lenient().when(u.getId()).thenReturn(id);
    return u;
  }
}
