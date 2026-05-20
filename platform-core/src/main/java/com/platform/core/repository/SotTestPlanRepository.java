package com.platform.core.repository;

import com.platform.core.domain.SotTestPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SotTestPlanRepository extends JpaRepository<SotTestPlan, UUID> {

    List<SotTestPlan> findByProjectId(UUID projectId);

    Optional<SotTestPlan> findByProjectIdAndReleaseId(UUID projectId, UUID releaseId);

    List<SotTestPlan> findByProjectIdAndState(UUID projectId, String state);
}
