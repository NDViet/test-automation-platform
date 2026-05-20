package com.platform.core.repository;

import com.platform.core.domain.SotReleaseRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SotReleaseRequirementRepository
        extends JpaRepository<SotReleaseRequirement, SotReleaseRequirement.PK> {

    List<SotReleaseRequirement> findByReleaseId(UUID releaseId);

    @Query("SELECT rr.requirementId FROM SotReleaseRequirement rr WHERE rr.releaseId = :releaseId")
    List<UUID> findRequirementIdsByReleaseId(@Param("releaseId") UUID releaseId);

    boolean existsByReleaseIdAndRequirementId(UUID releaseId, UUID requirementId);
}
