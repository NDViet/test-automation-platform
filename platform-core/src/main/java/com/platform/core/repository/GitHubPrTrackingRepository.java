package com.platform.core.repository;

import com.platform.core.domain.GitHubPrTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface GitHubPrTrackingRepository extends JpaRepository<GitHubPrTracking, UUID> {

    Optional<GitHubPrTracking> findByProjectIdAndRepoFullNameAndPrNumber(
            UUID projectId, String repoFullName, int prNumber);

    /** Remove tracking rows for PRs that haven't triggered anything in over 30 days (likely merged/closed). */
    @Modifying
    @Query("DELETE FROM GitHubPrTracking t WHERE t.projectId = :pid AND t.lastTriggeredAt < :cutoff")
    int deleteStaleByProject(@Param("pid") UUID projectId, @Param("cutoff") Instant cutoff);
}
