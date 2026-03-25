package com.platform.core.repository;

import com.platform.core.domain.PerformanceMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PerformanceMetricRepository extends JpaRepository<PerformanceMetric, UUID> {

    Optional<PerformanceMetric> findByExecutionId(UUID executionId);

    /** Most-recent N runs for a given project — used by trends queries. */
    @Query("""
            SELECT pm FROM PerformanceMetric pm
            JOIN pm.execution e
            WHERE e.project.id = :projectId
            ORDER BY e.executedAt DESC
            LIMIT :limit
            """)
    List<PerformanceMetric> findRecentByProjectId(
            @Param("projectId") UUID projectId,
            @Param("limit") int limit);
}
