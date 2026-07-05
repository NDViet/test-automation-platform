package com.platform.core.repository;

import com.platform.core.domain.QualityGate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QualityGateRepository extends JpaRepository<QualityGate, UUID> {

  Optional<QualityGate> findByProjectId(UUID projectId);
}
