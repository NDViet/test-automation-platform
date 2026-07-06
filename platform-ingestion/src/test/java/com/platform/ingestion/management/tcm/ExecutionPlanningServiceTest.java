package com.platform.ingestion.management.tcm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.platform.core.domain.TestCaseExecution;
import com.platform.core.domain.User;
import com.platform.core.domain.UserRole;
import com.platform.core.repository.PlatformTestCaseRepository;
import com.platform.core.repository.TestCaseExecutionRepository;
import com.platform.core.repository.UserRepository;
import com.platform.core.repository.UserRoleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecutionPlanningServiceTest {

  @Mock TestCaseExecutionRepository execRepo;
  @Mock PlatformTestCaseRepository testCaseRepo;
  @Mock UserRepository userRepo;
  @Mock UserRoleRepository userRoleRepo;

  ExecutionPlanningService service;

  private final UUID pid = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new ExecutionPlanningService(execRepo, testCaseRepo, userRepo, userRoleRepo);
    lenient().when(testCaseRepo.findById(any())).thenReturn(Optional.empty());
  }

  private User user(UUID id, String username, boolean enabled) {
    User u = new User(username, username + "@x.io", "hash", username, false, false);
    u.setEnabled(enabled);
    org.springframework.test.util.ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  private User user(String username, boolean enabled) {
    return user(UUID.randomUUID(), username, enabled);
  }

  private UserRole projectRole(UUID memberId) {
    return new UserRole(memberId, "TESTER", "PROJECT", pid, "admin");
  }

  @Test
  void myQueueReturnsAssignedExecutionsWithUsername() {
    when(userRepo.findById(userId)).thenReturn(Optional.of(user("bob", true)));
    TestCaseExecution e1 = new TestCaseExecution(UUID.randomUUID(), UUID.randomUUID());
    e1.assignTo(userId);
    TestCaseExecution e2 = new TestCaseExecution(UUID.randomUUID(), UUID.randomUUID());
    e2.assignTo(userId);
    when(execRepo.findMyQueue(pid, userId)).thenReturn(List.of(e1, e2));

    var queue = service.myQueue(pid, userId);

    assertThat(queue).hasSize(2);
    assertThat(queue).allMatch(d -> "bob".equals(d.assigneeUsername()));
  }

  @Test
  void assignableUsersListsProjectMembersWithEmailExcludingDisabled() {
    UUID aliceId = UUID.randomUUID();
    UUID ghostId = UUID.randomUUID();
    UUID carolId = UUID.randomUUID();
    when(userRoleRepo.findByScopeAndScopeId("PROJECT", pid))
        .thenReturn(List.of(projectRole(aliceId), projectRole(ghostId), projectRole(carolId)));
    when(userRepo.findAllById(any()))
        .thenReturn(
            List.of(
                user(aliceId, "alice", true),
                user(ghostId, "ghost", false),
                user(carolId, "carol", true)));

    var users = service.assignableUsers(pid);

    // Only enabled project members, sorted by username, each carrying their email.
    assertThat(users)
        .extracting(ExecutionPlanningService.AssignableUser::username)
        .containsExactly("alice", "carol");
    assertThat(users)
        .extracting(ExecutionPlanningService.AssignableUser::email)
        .containsExactly("alice@x.io", "carol@x.io");
  }

  @Test
  void assignableUsersFetchesOnlyDistinctProjectMemberIds() {
    UUID bobId = UUID.randomUUID();
    // Bob has two roles in the project — he must be fetched once, not twice.
    when(userRoleRepo.findByScopeAndScopeId("PROJECT", pid))
        .thenReturn(List.of(projectRole(bobId), projectRole(bobId)));
    @SuppressWarnings("unchecked")
    org.mockito.ArgumentCaptor<Iterable<UUID>> cap =
        org.mockito.ArgumentCaptor.forClass(Iterable.class);
    when(userRepo.findAllById(cap.capture())).thenReturn(List.of(user(bobId, "bob", true)));

    var users = service.assignableUsers(pid);

    assertThat(cap.getValue()).containsExactly(bobId);
    assertThat(users)
        .extracting(ExecutionPlanningService.AssignableUser::username)
        .containsExactly("bob");
  }

  @Test
  void assignableUsersEmptyWhenNoProjectMembers() {
    when(userRoleRepo.findByScopeAndScopeId(eq("PROJECT"), eq(pid))).thenReturn(List.of());
    assertThat(service.assignableUsers(pid)).isEmpty();
  }
}
