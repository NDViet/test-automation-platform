package com.platform.core.repository;

import com.platform.core.domain.GenerationClarification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GenerationClarificationRepository
    extends JpaRepository<GenerationClarification, UUID> {

  List<GenerationClarification> findByWorkflowIdOrderByRoundAsc(UUID workflowId);

  long countByWorkflowId(UUID workflowId);

  Optional<GenerationClarification> findFirstByWorkflowIdAndStatusOrderByRoundDesc(
      UUID workflowId, String status);
}
