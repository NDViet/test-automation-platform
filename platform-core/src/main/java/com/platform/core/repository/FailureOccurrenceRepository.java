package com.platform.core.repository;

import com.platform.core.domain.FailureOccurrence;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FailureOccurrenceRepository extends JpaRepository<FailureOccurrence, UUID> {

  boolean existsByClusterIdAndExecutionId(UUID clusterId, UUID executionId);

  long countByClusterId(UUID clusterId);

  List<FailureOccurrence> findTop20ByClusterIdOrderByOccurredAtDesc(UUID clusterId);
}
