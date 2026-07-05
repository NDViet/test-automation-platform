package com.platform.ingestion.management.tcm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.platform.core.domain.TestCaseExecution;
import com.platform.core.domain.User;
import com.platform.core.repository.PlatformTestCaseRepository;
import com.platform.core.repository.TestCaseExecutionRepository;
import com.platform.core.repository.UserRepository;
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

  ExecutionPlanningService service;

  private final UUID pid = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new ExecutionPlanningService(execRepo, testCaseRepo, userRepo);
    lenient().when(testCaseRepo.findById(any())).thenReturn(Optional.empty());
  }

  private User user(String username, boolean enabled) {
    User u = new User(username, username + "@x.io", "hash", username, false, false);
    u.setEnabled(enabled);
    org.springframework.test.util.ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
    return u;
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
  void assignableUsersExcludesDisabled() {
    when(userRepo.findAllByOrderByUsernameAsc())
        .thenReturn(List.of(user("alice", true), user("ghost", false), user("carol", true)));

    var users = service.assignableUsers();

    assertThat(users)
        .extracting(ExecutionPlanningService.AssignableUser::username)
        .containsExactly("alice", "carol");
  }
}
