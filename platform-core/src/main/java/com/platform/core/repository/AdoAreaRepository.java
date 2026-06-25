package com.platform.core.repository;

import com.platform.core.domain.AdoArea;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdoAreaRepository extends JpaRepository<AdoArea, UUID> {
  List<AdoArea> findByProjectIdOrderByPath(UUID projectId);

  Optional<AdoArea> findByProjectIdAndPath(UUID projectId, String path);

  long countByProjectId(UUID projectId);

  void deleteByProjectId(UUID projectId);
}
