package com.platform.core.repository;

import com.platform.core.domain.ImpactAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ImpactAnalysisRepository extends JpaRepository<ImpactAnalysis, UUID> {
    List<ImpactAnalysis> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
