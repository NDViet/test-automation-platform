package com.platform.core.repository;

import com.platform.core.domain.IssueTrackerLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IssueTrackerLinkRepository extends JpaRepository<IssueTrackerLink, UUID> {

    Optional<IssueTrackerLink> findByTestIdAndProjectIdAndTrackerType(
            String testId, UUID projectId, String trackerType);

    List<IssueTrackerLink> findByProjectIdAndTrackerType(UUID projectId, String trackerType);

    /** All linked test IDs for a project — used to find tests needing close/reopen. */
    @Query("SELECT l.testId FROM IssueTrackerLink l WHERE l.projectId = :projectId " +
           "AND l.trackerType = :trackerType")
    List<String> findTestIdsByProjectIdAndTrackerType(
            @Param("projectId") UUID projectId,
            @Param("trackerType") String trackerType);

    boolean existsByTestIdAndProjectIdAndTrackerType(
            String testId, UUID projectId, String trackerType);
}
