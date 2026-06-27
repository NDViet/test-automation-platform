package com.platform.core.repository;

import com.platform.core.domain.ExecutionAttachment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExecutionAttachmentRepository extends JpaRepository<ExecutionAttachment, UUID> {

  List<ExecutionAttachment> findByExecutionIdOrderByUploadedAtAsc(UUID executionId);

  List<ExecutionAttachment> findByTestRunId(UUID testRunId);
}
