package com.platform.core.repository;

import com.platform.core.domain.AdoIteration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdoIterationRepository extends JpaRepository<AdoIteration, UUID> {
    List<AdoIteration> findByProjectIdOrderByPath(UUID projectId);
    Optional<AdoIteration> findByProjectIdAndPath(UUID projectId, String path);
    long countByProjectId(UUID projectId);
    void deleteByProjectId(UUID projectId);
}
