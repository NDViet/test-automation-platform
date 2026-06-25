package com.platform.core.repository;

import com.platform.core.domain.SotRelease;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SotReleaseRepository extends JpaRepository<SotRelease, UUID> {

  List<SotRelease> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

  List<SotRelease> findByProjectIdAndState(UUID projectId, String state);

  Optional<SotRelease> findByProjectIdAndName(UUID projectId, String name);

  Optional<SotRelease> findByProjectIdAndExternalId(UUID projectId, String externalId);

  @Query(
      "SELECT r FROM SotRelease r WHERE r.projectId = :projectId AND r.state IN"
          + " ('PLANNED','IN_PROGRESS')")
  List<SotRelease> findActiveByProjectId(@Param("projectId") UUID projectId);
}
