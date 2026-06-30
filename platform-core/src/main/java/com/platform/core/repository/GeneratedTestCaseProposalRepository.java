package com.platform.core.repository;

import com.platform.core.domain.GeneratedTestCaseProposal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GeneratedTestCaseProposalRepository
    extends JpaRepository<GeneratedTestCaseProposal, UUID> {

  List<GeneratedTestCaseProposal> findByWorkflowIdOrderByOrdinalAsc(UUID workflowId);

  List<GeneratedTestCaseProposal> findByWorkflowIdAndStatusOrderByOrdinalAsc(
      UUID workflowId, String status);

  /** Scoped lookup so a tester can only touch proposals in a project they can operate. */
  Optional<GeneratedTestCaseProposal> findByIdAndProjectId(UUID id, UUID projectId);
}
