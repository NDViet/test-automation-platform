package com.platform.core.repository;

import com.platform.core.domain.AdoArea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdoAreaRepository extends JpaRepository<AdoArea, UUID> {
    List<AdoArea> findByProjectIdOrderByPath(UUID projectId);
    Optional<AdoArea> findByProjectIdAndPath(UUID projectId, String path);
    long countByProjectId(UUID projectId);
    void deleteByProjectId(UUID projectId);
}
