package com.platform.core.repository;

import com.platform.core.domain.NotificationPref;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationPrefRepository extends JpaRepository<NotificationPref, UUID> {

  List<NotificationPref> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

  Optional<NotificationPref> findByIdAndProjectId(UUID id, UUID projectId);

  /** Enabled routes for a project — the dispatch selection (Epic F6). */
  List<NotificationPref> findByProjectIdAndEnabledTrue(UUID projectId);
}
