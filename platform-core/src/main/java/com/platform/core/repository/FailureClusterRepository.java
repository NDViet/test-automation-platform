package com.platform.core.repository;

import com.platform.core.domain.FailureCluster;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FailureClusterRepository extends JpaRepository<FailureCluster, UUID> {

  Optional<FailureCluster> findByProjectIdAndSignatureHash(UUID projectId, String signatureHash);

  List<FailureCluster> findByProjectIdOrderByLastSeenAtDesc(UUID projectId);

  List<FailureCluster> findByProjectIdAndStatusOrderByLastSeenAtDesc(UUID projectId, String status);

  Optional<FailureCluster> findByIdAndProjectId(UUID id, UUID projectId);

  Optional<FailureCluster> findByRetestRunId(UUID retestRunId);

  long countByProjectIdAndStatus(UUID projectId, String status);
}
