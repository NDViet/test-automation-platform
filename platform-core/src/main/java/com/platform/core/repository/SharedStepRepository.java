package com.platform.core.repository;

import com.platform.core.domain.SharedStep;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SharedStepRepository extends JpaRepository<SharedStep, UUID> {

  List<SharedStep> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

  Optional<SharedStep> findByIdAndProjectId(UUID id, UUID projectId);
}
