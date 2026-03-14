package com.platform.core.repository;

import com.platform.common.enums.TestStatus;
import com.platform.core.domain.TestCaseResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TestCaseResultRepository extends JpaRepository<TestCaseResult, UUID> {

    List<TestCaseResult> findByExecutionId(UUID executionId);

    @Query("SELECT r FROM TestCaseResult r WHERE r.testId = :testId " +
           "AND r.execution.project.id = :projectId " +
           "ORDER BY r.createdAt DESC")
    List<TestCaseResult> findLatestByTestIdAndProjectId(
            @Param("testId") String testId,
            @Param("projectId") UUID projectId,
            Pageable pageable);

    @Query("SELECT r FROM TestCaseResult r WHERE r.testId = :testId " +
           "AND r.execution.project.id = :projectId " +
           "AND r.createdAt >= :since ORDER BY r.createdAt DESC")
    List<TestCaseResult> findByTestIdAndProjectIdSince(
            @Param("testId") String testId,
            @Param("projectId") UUID projectId,
            @Param("since") Instant since);

    @Query("SELECT r FROM TestCaseResult r WHERE r.execution.id = :executionId " +
           "AND r.status = :status")
    List<TestCaseResult> findByExecutionIdAndStatus(
            @Param("executionId") UUID executionId,
            @Param("status") TestStatus status);

    /** Fetches history with execution joined to avoid N+1 when reading environment. */
    @Query("SELECT r FROM TestCaseResult r JOIN FETCH r.execution e " +
           "WHERE r.testId = :testId AND e.project.id = :projectId " +
           "AND r.createdAt >= :since ORDER BY r.createdAt DESC")
    List<TestCaseResult> findWithExecutionByTestIdAndProjectIdSince(
            @Param("testId") String testId,
            @Param("projectId") UUID projectId,
            @Param("since") Instant since);

    /** All FAILED results across all projects since the given instant (for nightly batch). */
    @Query("SELECT r FROM TestCaseResult r JOIN FETCH r.execution e " +
           "WHERE r.status = :status AND r.createdAt >= :since ORDER BY r.createdAt DESC")
    List<TestCaseResult> findByStatusSince(
            @Param("status") TestStatus status,
            @Param("since") Instant since);
}
