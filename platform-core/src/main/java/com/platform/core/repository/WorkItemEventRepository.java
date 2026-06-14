package com.platform.core.repository;

import com.platform.core.domain.WorkItemEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface WorkItemEventRepository extends JpaRepository<WorkItemEvent, UUID> {

    boolean existsByProjectIdAndExternalIdAndRevAndEventTypeAndField(
            UUID projectId, String externalId, int rev, String eventType, String field);

    long countByProjectId(UUID projectId);

    @Modifying
    @Query("DELETE FROM WorkItemEvent e WHERE e.projectId = :pid AND e.externalId = :ext")
    void deleteByProjectIdAndExternalId(@Param("pid") UUID projectId, @Param("ext") String externalId);
}
