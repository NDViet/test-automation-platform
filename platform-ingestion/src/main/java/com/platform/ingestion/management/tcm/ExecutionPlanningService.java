package com.platform.ingestion.management.tcm;

import com.platform.core.domain.PlatformTestCase;
import com.platform.core.domain.TestCaseExecution;
import com.platform.core.domain.User;
import com.platform.core.domain.UserRole;
import com.platform.core.repository.PlatformTestCaseRepository;
import com.platform.core.repository.TestCaseExecutionRepository;
import com.platform.core.repository.UserRepository;
import com.platform.core.repository.UserRoleRepository;
import java.util.Comparator;
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
  private final UserRoleRepository userRoleRepo;

  public ExecutionPlanningService(
      TestCaseExecutionRepository execRepo,
      PlatformTestCaseRepository testCaseRepo,
      UserRepository userRepo,
      UserRoleRepository userRoleRepo) {
    this.execRepo = execRepo;
    this.testCaseRepo = testCaseRepo;
    this.userRepo = userRepo;
    this.userRoleRepo = userRoleRepo;
  }

  /** A user to whom an execution may be assigned (or who triggered a run). */
  public record AssignableUser(String id, String username, String displayName, String email) {}

  public List<TestCaseExecutionDto> myQueue(UUID projectId, UUID userId) {
    String username = userRepo.findById(userId).map(User::getUsername).orElse(null);
    return execRepo.findMyQueue(projectId, userId).stream()
        .map(e -> TestCaseExecutionDto.from(e, title(e), username))
        .toList();
  }

  /**
   * The enabled users who are members of the project (have a PROJECT-scoped role for it), for the
   * Triggered By / assignee pickers. Scoped so the picker offers people actually on the project,
   * and carries each member's email so the UI can search by it.
   */
  public List<AssignableUser> assignableUsers(UUID projectId) {
    List<UUID> memberIds =
        userRoleRepo.findByScopeAndScopeId("PROJECT", projectId).stream()
            .map(UserRole::getUserId)
            .distinct()
            .toList();
    if (memberIds.isEmpty()) {
      return List.of();
    }
    return userRepo.findAllById(memberIds).stream()
        .filter(User::isEnabled)
        .sorted(Comparator.comparing(User::getUsername))
        .map(
            u ->
                new AssignableUser(
                    u.getId().toString(), u.getUsername(), u.getDisplayName(), u.getEmail()))
        .toList();
  }

  private String title(TestCaseExecution e) {
    return testCaseRepo.findById(e.getTestCaseId()).map(PlatformTestCase::getTitle).orElse(null);
  }
}
