package com.platform.core.repository;

import com.platform.core.domain.PlatformTestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatformTestCaseRepository extends JpaRepository<PlatformTestCase, UUID> {

    List<PlatformTestCase> findByProjectId(UUID projectId);

    Optional<PlatformTestCase> findByProjectIdAndExternalId(UUID projectId, String externalId);

    List<PlatformTestCase> findByProjectIdAndCoverageStatus(UUID projectId, String coverageStatus);

    @Query("SELECT tc FROM PlatformTestCase tc WHERE tc.projectId = :projectId AND tc.hasAutomation = true")
    List<PlatformTestCase> findAutomatedByProjectId(@Param("projectId") UUID projectId);

    @Query(value = """
            SELECT tc.* FROM platform_test_cases tc
            JOIN platform_traceability_edges e
              ON e.from_id = tc.id AND e.from_tier = 'TEST_CASE'
             AND e.to_id = :requirementId AND e.to_tier = 'REQUIREMENT'
             AND e.edge_type = 'COVERED_BY'
            WHERE tc.project_id = :projectId
            """, nativeQuery = true)
    List<PlatformTestCase> findCoveringTestCases(@Param("projectId") UUID projectId,
                                                  @Param("requirementId") UUID requirementId);

    @Query("SELECT COUNT(tc) FROM PlatformTestCase tc WHERE tc.projectId = :projectId AND tc.coverageStatus = 'ACTIVE'")
    long countActiveByProjectId(@Param("projectId") UUID projectId);

    List<PlatformTestCase> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<PlatformTestCase> findByProjectIdAndStatus(UUID projectId, String status);

    List<PlatformTestCase> findByProjectIdAndSuiteId(UUID projectId, UUID suiteId);

    List<PlatformTestCase> findByProjectIdAndStatusAndSuiteId(UUID projectId, String status, UUID suiteId);

    long countByProjectId(UUID projectId);

    long countByProjectIdAndStatus(UUID projectId, String status);

    @Query("SELECT tc FROM PlatformTestCase tc WHERE tc.projectId = :projectId AND " +
           "(:search IS NULL OR LOWER(tc.title) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<PlatformTestCase> searchByProjectId(@Param("projectId") UUID projectId, @Param("search") String search);
}
