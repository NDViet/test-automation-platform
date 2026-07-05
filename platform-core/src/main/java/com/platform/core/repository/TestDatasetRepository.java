package com.platform.core.repository;

import com.platform.core.domain.TestDataset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestDatasetRepository extends JpaRepository<TestDataset, UUID> {

  List<TestDataset> findByProjectIdOrderByNameAsc(UUID projectId);

  Optional<TestDataset> findByIdAndProjectId(UUID id, UUID projectId);
}
