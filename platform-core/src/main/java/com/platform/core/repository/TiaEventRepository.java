package com.platform.core.repository;

import com.platform.core.domain.TiaEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TiaEventRepository extends JpaRepository<TiaEvent, UUID> {

    /** All events for a project in descending time order — for the events history table. */
    List<TiaEvent> findByProjectIdOrderByQueriedAtDesc(UUID projectId);

    /** Events within a time window — for trend queries. */
    @Query("""
            SELECT e FROM TiaEvent e
            WHERE e.projectId = :projectId
              AND e.queriedAt >= :from
            ORDER BY e.queriedAt ASC
            """)
    List<TiaEvent> findByProjectIdSince(@Param("projectId") UUID projectId,
                                        @Param("from") Instant from);

    /** Aggregate: average reduction % per project over a time window. */
    @Query("""
            SELECT AVG(e.reductionPct) FROM TiaEvent e
            WHERE e.projectId = :projectId
              AND e.queriedAt >= :from
              AND e.reductionPct IS NOT NULL
            """)
    Double avgReductionPct(@Param("projectId") UUID projectId,
                           @Param("from") Instant from);

    /** Count events by risk level for a project (for risk distribution chart). */
    @Query("""
            SELECT e.riskLevel, COUNT(e) FROM TiaEvent e
            WHERE e.projectId = :projectId
              AND e.queriedAt >= :from
            GROUP BY e.riskLevel
            """)
    List<Object[]> countByRiskLevel(@Param("projectId") UUID projectId,
                                    @Param("from") Instant from);

    /** Total events across all projects in a time window (org-level stat). */
    @Query("SELECT COUNT(e) FROM TiaEvent e WHERE e.queriedAt >= :from")
    long countAllSince(@Param("from") Instant from);

    /** Org-level: average reduction % across all projects in a time window. */
    @Query("""
            SELECT AVG(e.reductionPct) FROM TiaEvent e
            WHERE e.queriedAt >= :from
              AND e.reductionPct IS NOT NULL
            """)
    Double avgReductionPctAll(@Param("from") Instant from);

    /** Daily points for a project — queried_at truncated to day, avg reduction, count. */
    @Query(value = """
            SELECT date_trunc('day', e.queried_at) AS day,
                   AVG(e.reduction_pct)            AS avg_reduction,
                   COUNT(*)                        AS event_count,
                   AVG(e.selected_tests)           AS avg_selected,
                   AVG(e.total_tests)              AS avg_total
            FROM tia_events e
            WHERE e.project_id = :projectId
              AND e.queried_at >= :from
            GROUP BY date_trunc('day', e.queried_at)
            ORDER BY day ASC
            """, nativeQuery = true)
    List<Object[]> dailyStatsForProject(@Param("projectId") UUID projectId,
                                        @Param("from") Instant from);
}
