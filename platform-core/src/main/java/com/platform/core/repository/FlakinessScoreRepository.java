package com.platform.core.repository;

import com.platform.core.domain.FlakinessScore;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FlakinessScoreRepository extends JpaRepository<FlakinessScore, UUID> {

    Optional<FlakinessScore> findByTestIdAndProjectId(String testId, UUID projectId);

    long countByClassification(FlakinessScore.Classification classification);

    @Query("SELECT f FROM FlakinessScore f WHERE f.projectId = :projectId " +
           "ORDER BY f.score DESC")
    List<FlakinessScore> findTopFlakyByProject(
            @Param("projectId") UUID projectId, Pageable pageable);

    @Query("SELECT f FROM FlakinessScore f ORDER BY f.score DESC")
    List<FlakinessScore> findTopFlakyAcrossOrg(Pageable pageable);

    @Modifying
    @Query(value =
        "INSERT INTO flakiness_scores " +
        "    (id, test_id, project_id, score, classification, total_runs, " +
        "     failure_count, failure_rate, last_failed_at, last_passed_at, computed_at) " +
        "VALUES (gen_random_uuid(), :testId, :projectId, :score, " +
        "        :classification, :totalRuns, :failureCount, " +
        "        :failureRate, :lastFailedAt, :lastPassedAt, now()) " +
        "ON CONFLICT (test_id, project_id) DO UPDATE SET " +
        "    score = EXCLUDED.score, " +
        "    classification = EXCLUDED.classification, " +
        "    total_runs = EXCLUDED.total_runs, " +
        "    failure_count = EXCLUDED.failure_count, " +
        "    failure_rate = EXCLUDED.failure_rate, " +
        "    last_failed_at = EXCLUDED.last_failed_at, " +
        "    last_passed_at = EXCLUDED.last_passed_at, " +
        "    computed_at = now()",
        nativeQuery = true)
    void upsert(
        @Param("testId") String testId,
        @Param("projectId") java.util.UUID projectId,
        @Param("score") java.math.BigDecimal score,
        @Param("classification") String classification,
        @Param("totalRuns") int totalRuns,
        @Param("failureCount") int failureCount,
        @Param("failureRate") java.math.BigDecimal failureRate,
        @Param("lastFailedAt") java.time.Instant lastFailedAt,
        @Param("lastPassedAt") java.time.Instant lastPassedAt
    );
}
