package com.platform.core.repository;

import com.platform.core.domain.TestCoverageMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestCoverageMappingRepository extends JpaRepository<TestCoverageMapping, UUID> {

    /** Returns all mappings for the given project that cover any of the listed class names. */
    List<TestCoverageMapping> findByProjectIdAndClassNameIn(UUID projectId, Collection<String> classNames);

    /** Returns all class names covered by a given test in a project. */
    List<TestCoverageMapping> findByProjectIdAndTestCaseId(UUID projectId, String testCaseId);

    /** Lookup for upsert — find exact triple. */
    Optional<TestCoverageMapping> findByProjectIdAndTestCaseIdAndClassName(
            UUID projectId, String testCaseId, String className);

    /**
     * Returns all wildcard coverage entries for a project — entries where className
     * ends with {@code .*} (e.g. {@code com.example.payment.*}).
     * Used by TestImpactService to resolve wildcard @AffectedBy annotations.
     */
    @Query("SELECT m FROM TestCoverageMapping m WHERE m.projectId = :projectId AND m.className LIKE '%.*'")
    List<TestCoverageMapping> findWildcardMappingsByProject(@Param("projectId") UUID projectId);

    /** Count distinct covered classes for a project (coverage breadth metric). */
    @Query("SELECT COUNT(DISTINCT m.className) FROM TestCoverageMapping m WHERE m.projectId = :projectId")
    long countDistinctClassesByProject(@Param("projectId") UUID projectId);

    /** Count distinct tests that have coverage data for a project. */
    @Query("SELECT COUNT(DISTINCT m.testCaseId) FROM TestCoverageMapping m WHERE m.projectId = :projectId")
    long countMappedTestsByProject(@Param("projectId") UUID projectId);

    /**
     * Daily coverage breadth snapshots — distinct classes and tests seen per day,
     * derived from last_seen_at. Used by Grafana to plot coverage growth over time.
     */
    @Query(value = """
            SELECT date_trunc('day', m.last_seen_at)  AS day,
                   COUNT(DISTINCT m.class_name)        AS distinct_classes,
                   COUNT(DISTINCT m.test_case_id)      AS distinct_tests
            FROM test_coverage_mappings m
            WHERE m.project_id = :projectId
              AND m.last_seen_at >= :from
            GROUP BY date_trunc('day', m.last_seen_at)
            ORDER BY day ASC
            """, nativeQuery = true)
    List<Object[]> dailyCoverageBreadth(@Param("projectId") UUID projectId,
                                        @Param("from") Instant from);

    /** Coverage per project — for org-level coverage breadth table. */
    @Query(value = """
            SELECT p.slug                            AS project_slug,
                   t.slug                            AS team_slug,
                   COUNT(DISTINCT m.class_name)      AS distinct_classes,
                   COUNT(DISTINCT m.test_case_id)    AS distinct_tests,
                   MAX(m.last_seen_at)               AS last_seen_at
            FROM test_coverage_mappings m
            JOIN projects p ON p.id = m.project_id
            JOIN teams    t ON t.id = p.team_id
            GROUP BY p.slug, t.slug
            ORDER BY distinct_tests DESC
            """, nativeQuery = true)
    List<Object[]> coverageBreadthByProject();

    @Modifying
    @Query("DELETE FROM TestCoverageMapping m WHERE m.projectId = :projectId")
    void deleteByProjectId(@Param("projectId") UUID projectId);
}
