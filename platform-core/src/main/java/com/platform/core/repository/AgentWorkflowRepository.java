package com.platform.core.repository;

import com.platform.core.domain.AgentWorkflow;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentWorkflowRepository extends JpaRepository<AgentWorkflow, UUID> {
  List<AgentWorkflow> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

  List<AgentWorkflow> findByProjectIdAndStatusOrderByCreatedAtDesc(UUID projectId, String status);

  @Query(
      "SELECT w FROM AgentWorkflow w WHERE w.projectId = :pid AND w.status IN"
          + " ('PENDING','RUNNING','AWAITING_REVIEW') ORDER BY w.createdAt DESC")
  List<AgentWorkflow> findActiveByProjectId(@Param("pid") UUID projectId);
}
