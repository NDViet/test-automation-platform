package com.platform.core.repository;

import com.platform.core.domain.FailureAnalysis;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FailureAnalysisRepository extends JpaRepository<FailureAnalysis, UUID> {

    List<FailureAnalysis> findByTestIdAndProjectIdOrderByAnalysedAtDesc(
            String testId, UUID projectId, Pageable pageable);

    Optional<FailureAnalysis> findTopByTestIdAndProjectIdOrderByAnalysedAtDesc(
            String testId, UUID projectId);

    @Query("SELECT f FROM FailureAnalysis f WHERE f.projectId = :projectId " +
           "AND f.analysedAt >= :since ORDER BY f.analysedAt DESC")
    List<FailureAnalysis> findByProjectIdSince(
            @Param("projectId") UUID projectId,
            @Param("since") Instant since);

    @Query("SELECT f FROM FailureAnalysis f WHERE f.projectId = :projectId " +
           "AND f.category = :category ORDER BY f.analysedAt DESC")
    List<FailureAnalysis> findByProjectIdAndCategory(
            @Param("projectId") UUID projectId,
            @Param("category") String category,
            Pageable pageable);

    /** Returns true only when a SUCCESSFUL analysis exists — ERROR records are retried. */
    boolean existsByTestCaseResultIdAndAnalysisStatus(UUID testCaseResultId, String analysisStatus);

    /** Convenience wrapper — callers use this instead of the raw method. */
    default boolean existsSuccessfulAnalysis(UUID testCaseResultId) {
        return existsByTestCaseResultIdAndAnalysisStatus(testCaseResultId, "SUCCESS");
    }
}
