package com.platform.core.repository;

import com.platform.core.domain.AdoTeam;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdoTeamRepository extends JpaRepository<AdoTeam, UUID> {
  List<AdoTeam> findByProjectIdOrderByName(UUID projectId);

  Optional<AdoTeam> findByProjectIdAndAdoId(UUID projectId, String adoId);

  long countByProjectId(UUID projectId);

  void deleteByProjectId(UUID projectId);
}
