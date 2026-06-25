package com.platform.core.repository;

import com.platform.core.domain.AgentWorkflowStep;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentWorkflowStepRepository extends JpaRepository<AgentWorkflowStep, UUID> {
  List<AgentWorkflowStep> findByWorkflowIdOrderBySequenceOrder(UUID workflowId);

  List<AgentWorkflowStep> findByWorkflowIdAndStatus(UUID workflowId, String status);
}
