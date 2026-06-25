package com.platform.core.repository;

import com.platform.core.domain.Team;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, UUID> {
  List<Team> findByProjectIdOrderByNameAsc(UUID projectId);

  Optional<Team> findByProjectIdAndSlug(UUID projectId, String slug);

  boolean existsByProjectIdAndSlug(UUID projectId, String slug);
}
