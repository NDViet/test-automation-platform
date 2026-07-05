package com.platform.ingestion.management.tcm;

import com.platform.core.domain.PlatformTestCase;
import com.platform.core.domain.TestCaseExecution;
import com.platform.core.domain.User;
import com.platform.core.repository.PlatformTestCaseRepository;
import com.platform.core.repository.TestCaseExecutionRepository;
import com.platform.core.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read models for execution planning: a tester's "My Queue" (executions assigned to them across a
 * project) and the assignable-user picker.
 */
@Service
@Transactional(readOnly = true)
public class ExecutionPlanningService {

  private final TestCaseExecutionRepository execRepo;
  private final PlatformTestCaseRepository testCaseRepo;
  private final UserRepository userRepo;

  public ExecutionPlanningService(
      TestCaseExecutionRepository execRepo,
      PlatformTestCaseRepository testCaseRepo,
      UserRepository userRepo) {
    this.execRepo = execRepo;
    this.testCaseRepo = testCaseRepo;
    this.userRepo = userRepo;
  }

  /** A user to whom an execution may be assigned. */
  public record AssignableUser(String id, String username, String displayName) {}

  public List<TestCaseExecutionDto> myQueue(UUID projectId, UUID userId) {
    String username = userRepo.findById(userId).map(User::getUsername).orElse(null);
    return execRepo.findMyQueue(projectId, userId).stream()
        .map(e -> TestCaseExecutionDto.from(e, title(e), username))
        .toList();
  }

  public List<AssignableUser> assignableUsers() {
    return userRepo.findAllByOrderByUsernameAsc().stream()
        .filter(User::isEnabled)
        .map(u -> new AssignableUser(u.getId().toString(), u.getUsername(), u.getDisplayName()))
        .toList();
  }

  private String title(TestCaseExecution e) {
    return testCaseRepo.findById(e.getTestCaseId()).map(PlatformTestCase::getTitle).orElse(null);
  }
}
