package com.platform.core.repository;

import com.platform.core.domain.TestCoverageMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** Count distinct covered classes for a project (coverage breadth metric). */
    @Query("SELECT COUNT(DISTINCT m.className) FROM TestCoverageMapping m WHERE m.projectId = :projectId")
    long countDistinctClassesByProject(@Param("projectId") UUID projectId);

    /** Count distinct tests that have coverage data for a project. */
    @Query("SELECT COUNT(DISTINCT m.testCaseId) FROM TestCoverageMapping m WHERE m.projectId = :projectId")
    long countMappedTestsByProject(@Param("projectId") UUID projectId);

    @Modifying
    @Query("DELETE FROM TestCoverageMapping m WHERE m.projectId = :projectId")
    void deleteByProjectId(@Param("projectId") UUID projectId);
}
