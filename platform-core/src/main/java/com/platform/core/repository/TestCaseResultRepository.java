package com.platform.core.repository;

import com.platform.common.enums.TestStatus;
import com.platform.core.domain.TestCaseResult;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TestCaseResultRepository extends JpaRepository<TestCaseResult, UUID> {

  List<TestCaseResult> findByExecutionId(UUID executionId);

  Optional<TestCaseResult> findFirstByExecution_RunIdAndTestId(String runId, String testId);

  @Query(
      "SELECT r FROM TestCaseResult r WHERE r.testId = :testId "
          + "AND r.execution.project.id = :projectId "
          + "ORDER BY r.createdAt DESC")
  List<TestCaseResult> findLatestByTestIdAndProjectId(
      @Param("testId") String testId, @Param("projectId") UUID projectId, Pageable pageable);

  @Query(
      "SELECT r FROM TestCaseResult r WHERE r.testId = :testId "
          + "AND r.execution.project.id = :projectId "
          + "AND r.createdAt >= :since ORDER BY r.createdAt DESC")
  List<TestCaseResult> findByTestIdAndProjectIdSince(
      @Param("testId") String testId,
      @Param("projectId") UUID projectId,
      @Param("since") Instant since);

  @Query(
      "SELECT r FROM TestCaseResult r WHERE r.execution.id = :executionId "
          + "AND r.status = :status")
  List<TestCaseResult> findByExecutionIdAndStatus(
      @Param("executionId") UUID executionId, @Param("status") TestStatus status);

  /** Fetches history with execution joined to avoid N+1 when reading environment. */
  @Query(
      "SELECT r FROM TestCaseResult r JOIN FETCH r.execution e "
          + "WHERE r.testId = :testId AND e.project.id = :projectId "
          + "AND r.createdAt >= :since ORDER BY r.createdAt DESC")
  List<TestCaseResult> findWithExecutionByTestIdAndProjectIdSince(
      @Param("testId") String testId,
      @Param("projectId") UUID projectId,
      @Param("since") Instant since);

  /** All FAILED results across all projects since the given instant (for nightly batch). */
  @Query(
      "SELECT r FROM TestCaseResult r JOIN FETCH r.execution e "
          + "WHERE r.status = :status AND r.createdAt >= :since ORDER BY r.createdAt DESC")
  List<TestCaseResult> findByStatusSince(
      @Param("status") TestStatus status, @Param("since") Instant since);

  /** FAILED or BROKEN results across all projects since the given instant (for nightly batch). */
  @Query(
      "SELECT r FROM TestCaseResult r JOIN FETCH r.execution e "
          + "WHERE r.status IN :statuses AND r.createdAt >= :since ORDER BY r.createdAt DESC")
  List<TestCaseResult> findByStatusInSince(
      @Param("statuses") Collection<TestStatus> statuses, @Param("since") Instant since);

  /** FAILED or BROKEN results for a specific execution (for real-time AI consumer). */
  @Query(
      "SELECT r FROM TestCaseResult r WHERE r.execution.id = :executionId "
          + "AND r.status IN :statuses")
  List<TestCaseResult> findByExecutionIdAndStatusIn(
      @Param("executionId") UUID executionId, @Param("statuses") Collection<TestStatus> statuses);

  /** All results for a project since the given instant; execution is join-fetched to avoid N+1. */
  @Query(
      "SELECT r FROM TestCaseResult r JOIN FETCH r.execution e "
          + "WHERE e.project.id = :projectId "
          + "AND r.createdAt >= :since "
          + "ORDER BY r.createdAt DESC")
  List<TestCaseResult> findByProjectSince(
      @Param("projectId") UUID projectId, @Param("since") Instant since);

  /** Per-status counts for one execution — used by finishRun to write accurate aggregates. */
  @Query(
      "SELECT r.status, COUNT(r) FROM TestCaseResult r WHERE r.execution.id = :executionId GROUP BY"
          + " r.status")
  List<Object[]> countGroupByStatusForExecution(@Param("executionId") UUID executionId);
}
