package com.platform.core.repository;

import com.platform.core.domain.TestExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

public interface TestExecutionRepository extends JpaRepository<TestExecution, UUID> {

    Optional<TestExecution> findByRunId(String runId);

    boolean existsByRunId(String runId);

    Page<TestExecution> findByProjectIdOrderByExecutedAtDesc(UUID projectId, Pageable pageable);

    Page<TestExecution> findByProjectIdAndBranchOrderByExecutedAtDesc(
            UUID projectId, String branch, Pageable pageable);

    @Query("SELECT e FROM TestExecution e WHERE e.project.id = :projectId " +
           "AND e.executedAt BETWEEN :from AND :to ORDER BY e.executedAt DESC")
    List<TestExecution> findByProjectAndDateRange(
            @Param("projectId") UUID projectId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("SELECT AVG(CAST(e.passed AS double) / NULLIF(e.totalTests, 0)) " +
           "FROM TestExecution e WHERE e.project.id = :projectId " +
           "AND e.executedAt >= :since AND e.totalTests > 0")
    Double computePassRate(@Param("projectId") UUID projectId, @Param("since") Instant since);

    Optional<TestExecution> findTopByProjectIdOrderByExecutedAtDesc(UUID projectId);

    @Query("SELECT e FROM TestExecution e WHERE e.project.id IN :projectIds " +
           "AND e.executedAt >= :since ORDER BY e.executedAt DESC")
    List<TestExecution> findByProjectIdsAndSince(
            @Param("projectIds") Set<UUID> projectIds,
            @Param("since") Instant since);
}
