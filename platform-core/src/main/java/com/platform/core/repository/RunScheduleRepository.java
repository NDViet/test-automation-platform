package com.platform.core.repository;

import com.platform.core.domain.RunSchedule;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RunScheduleRepository extends JpaRepository<RunSchedule, UUID> {

  List<RunSchedule> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

  Optional<RunSchedule> findByIdAndProjectId(UUID id, UUID projectId);

  /** Enabled schedules whose next run is due at or before {@code now}. */
  @Query(
      "SELECT s FROM RunSchedule s WHERE s.enabled = true AND s.nextRunAt IS NOT NULL"
          + " AND s.nextRunAt <= :now")
  List<RunSchedule> findDueEnabled(@Param("now") Instant now);
}
