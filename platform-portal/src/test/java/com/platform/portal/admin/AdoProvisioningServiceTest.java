package com.platform.portal.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.core.domain.AdoUser;
import com.platform.core.domain.User;
import com.platform.core.domain.UserRole;
import com.platform.core.repository.AdoUserRepository;
import com.platform.core.repository.UserRepository;
import com.platform.core.repository.UserRoleRepository;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AdoProvisioningServiceTest {

  @Mock UserRepository users;
  @Mock UserRoleRepository roles;
  @Mock PasswordEncoder encoder;
  @Mock AdoUserRepository adoUsers;

  AdoProvisioningService service;

  private final UUID pid = UUID.randomUUID();
  private final AuthenticatedUser superActor =
      new AuthenticatedUser(UUID.randomUUID(), "root", true);

  @BeforeEach
  void setUp() {
    service = new AdoProvisioningService(users, roles, encoder, adoUsers);
  }

  private AdoUser adoMember(
      String displayName, String uniqueName, String email, boolean teamMember) {
    AdoUser u = new AdoUser(pid, uniqueName, displayName);
    ReflectionTestUtils.setField(u, "email", email);
    ReflectionTestUtils.setField(u, "teamMember", teamMember);
    return u;
  }

  @Test
  void createsEnabledLoginLockedUserWithTesterGrant() {
    when(adoUsers.findByProjectIdOrderByDisplayName(pid))
        .thenReturn(List.of(adoMember("Alice A", "alice@int.hrs.com", "alice@int.hrs.com", true)));
    when(users.findByEmailIgnoreCase("alice@int.hrs.com")).thenReturn(Optional.empty());
    when(users.existsByUsername(any())).thenReturn(false);
    when(encoder.encode(any())).thenReturn("RND-HASH");
    when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    when(roles.findByUserIdAndRoleAndScopeAndScopeId(any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    when(roles.save(any(UserRole.class))).thenAnswer(inv -> inv.getArgument(0));

    var summary = service.provisionProject(pid, superActor);

    ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
    verify(users).save(userCap.capture());
    User created = userCap.getValue();
    assertThat(created.getEmail()).isEqualTo("alice@int.hrs.com");
    assertThat(created.isEnabled()).isTrue(); // assignable
    assertThat(created.isMustChangePassword()).isTrue(); // must set a real password
    assertThat(created.isSuperAdmin()).isFalse();
    assertThat(created.getPasswordHash()).isEqualTo("RND-HASH"); // unknown → login-locked

    ArgumentCaptor<UserRole> roleCap = ArgumentCaptor.forClass(UserRole.class);
    verify(roles).save(roleCap.capture());
    assertThat(roleCap.getValue().getRole()).isEqualTo("TESTER");
    assertThat(roleCap.getValue().getScope()).isEqualTo("PROJECT");
    assertThat(roleCap.getValue().getScopeId()).isEqualTo(pid);

    assertThat(summary.total()).isEqualTo(1);
    assertThat(summary.created()).isEqualTo(1);
    assertThat(summary.granted()).isEqualTo(1);
    assertThat(summary.skippedNoEmail()).isZero();
  }

  @Test
  void neverEncodesAKnownPasswordValue() {
    when(adoUsers.findByProjectIdOrderByDisplayName(pid))
        .thenReturn(List.of(adoMember("Bob B", "bob@int.hrs.com", "bob@int.hrs.com", true)));
    when(users.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
    when(users.existsByUsername(any())).thenReturn(false);
    when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    when(roles.findByUserIdAndRoleAndScopeAndScopeId(any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    when(roles.save(any(UserRole.class))).thenAnswer(inv -> inv.getArgument(0));
    ArgumentCaptor<String> pw = ArgumentCaptor.forClass(String.class);
    when(encoder.encode(pw.capture())).thenReturn("RND-HASH");

    service.provisionProject(pid, superActor);

    // The encoded secret must be high-entropy, not a fixed/guessable default.
    assertThat(pw.getValue()).hasSizeGreaterThanOrEqualTo(24);
    assertThat(pw.getValue()).isNotIn("changeme", "password", "temp", "");
  }

  @Test
  void linksExistingEnabledUserWithoutTouchingItWhenAlreadyGranted() {
    User existing = new User("alice", "alice@int.hrs.com", "H", "Alice", false, false);
    existing.setEnabled(true);
    ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
    when(adoUsers.findByProjectIdOrderByDisplayName(pid))
        .thenReturn(List.of(adoMember("Alice A", "alice@int.hrs.com", "alice@int.hrs.com", true)));
    when(users.findByEmailIgnoreCase("alice@int.hrs.com")).thenReturn(Optional.of(existing));
    when(roles.findByUserIdAndRoleAndScopeAndScopeId(existing.getId(), "TESTER", "PROJECT", pid))
        .thenReturn(Optional.of(new UserRole(existing.getId(), "TESTER", "PROJECT", pid, "root")));

    var summary = service.provisionProject(pid, superActor);

    verify(users, never()).save(any()); // already enabled → nothing to change
    verify(roles, never()).save(any());
    assertThat(summary.linked()).isEqualTo(1);
    assertThat(summary.created()).isZero();
    assertThat(summary.activated()).isZero();
    assertThat(summary.alreadyMembers()).isEqualTo(1);
    assertThat(summary.granted()).isZero();
  }

  @Test
  void activatesLinkedDisabledShadowUserSoItBecomesAssignable() {
    // ADO shadow accounts are created disabled + login-locked by the ADO bootstrap; provisioning
    // must enable them (its whole purpose is to make the person assignable).
    User shadow = new User("bob", "bob@int.hrs.com", "LOCKED", "Bob", false, true);
    shadow.setEnabled(false);
    ReflectionTestUtils.setField(shadow, "id", UUID.randomUUID());
    when(adoUsers.findByProjectIdOrderByDisplayName(pid))
        .thenReturn(List.of(adoMember("Bob B", "bob@int.hrs.com", "bob@int.hrs.com", true)));
    when(users.findByEmailIgnoreCase("bob@int.hrs.com")).thenReturn(Optional.of(shadow));
    when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    when(roles.findByUserIdAndRoleAndScopeAndScopeId(shadow.getId(), "TESTER", "PROJECT", pid))
        .thenReturn(Optional.empty());
    when(roles.save(any(UserRole.class))).thenAnswer(inv -> inv.getArgument(0));

    var summary = service.provisionProject(pid, superActor);

    assertThat(shadow.isEnabled()).isTrue(); // now assignable
    assertThat(shadow.getPasswordHash())
        .isEqualTo("LOCKED"); // still login-locked; password untouched
    verify(users).save(shadow);
    assertThat(summary.linked()).isEqualTo(1);
    assertThat(summary.activated()).isEqualTo(1);
    assertThat(summary.granted()).isEqualTo(1);
  }

  @Test
  void provisionsEveryoneWithEmailRegardlessOfTeamMembershipAndSkipsNoEmail() {
    when(adoUsers.findByProjectIdOrderByDisplayName(pid))
        .thenReturn(
            List.of(
                adoMember("No Email", "noemail", null, true),
                adoMember("Blank", "blank", "  ", false),
                adoMember("Outsider", "out@x.io", "out@x.io", false), // non-team, has email
                adoMember("Member", "mem@x.io", "mem@x.io", true)));
    when(users.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
    when(users.existsByUsername(any())).thenReturn(false);
    when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    when(roles.findByUserIdAndRoleAndScopeAndScopeId(any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    when(roles.save(any(UserRole.class))).thenAnswer(inv -> inv.getArgument(0));

    var summary = service.provisionProject(pid, superActor);

    // Everyone shown in the Users tab with an email is provisioned — team membership is irrelevant.
    assertThat(summary.total()).isEqualTo(2); // Outsider + Member
    assertThat(summary.created()).isEqualTo(2);
    assertThat(summary.skippedNoEmail()).isEqualTo(2); // No Email + Blank
  }

  @Test
  void nonAdminActorIsForbidden() {
    AuthenticatedUser plain = new AuthenticatedUser(UUID.randomUUID(), "joe", false);
    when(roles.findByUserId(plain.userId())).thenReturn(List.of());

    assertThatThrownBy(() -> service.provisionProject(pid, plain))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    verify(adoUsers, never()).findByProjectIdOrderByDisplayName(any());
  }

  @Test
  void orgAdminActorIsAllowed() {
    AuthenticatedUser orgAdmin = new AuthenticatedUser(UUID.randomUUID(), "oa", false);
    lenient()
        .when(roles.findByUserId(orgAdmin.userId()))
        .thenReturn(
            List.of(new UserRole(orgAdmin.userId(), "ORG_ADMIN", "ORG", UUID.randomUUID(), "x")));
    when(adoUsers.findByProjectIdOrderByDisplayName(pid)).thenReturn(List.of());

    var summary = service.provisionProject(pid, orgAdmin);

    assertThat(summary.total()).isZero();
  }
}
