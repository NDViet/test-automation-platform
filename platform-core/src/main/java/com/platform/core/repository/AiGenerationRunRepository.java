package com.platform.core.repository;

import com.platform.core.domain.AiGenerationRun;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiGenerationRunRepository extends JpaRepository<AiGenerationRun, UUID> {

  Optional<AiGenerationRun> findByWorkflowId(UUID workflowId);
}
