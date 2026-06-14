package com.platform.core.repository;

import com.platform.core.domain.AdoTeam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdoTeamRepository extends JpaRepository<AdoTeam, UUID> {
    List<AdoTeam> findByProjectIdOrderByName(UUID projectId);
    Optional<AdoTeam> findByProjectIdAndAdoId(UUID projectId, String adoId);
    long countByProjectId(UUID projectId);
    void deleteByProjectId(UUID projectId);
}
