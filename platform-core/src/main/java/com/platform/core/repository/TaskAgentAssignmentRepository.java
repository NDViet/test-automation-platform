package com.platform.core.repository;

import com.platform.core.domain.TaskAgentAssignment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskAgentAssignmentRepository extends JpaRepository<TaskAgentAssignment, UUID> {

  List<TaskAgentAssignment> findByScopeAndScopeId(String scope, UUID scopeId);

  Optional<TaskAgentAssignment> findByScopeAndScopeIdAndTaskTypeAndSubType(
      String scope, UUID scopeId, String taskType, String subType);
}
