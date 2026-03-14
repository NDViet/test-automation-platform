package com.platform.core.repository;

import com.platform.core.domain.AlertHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AlertHistoryRepository extends JpaRepository<AlertHistory, UUID> {

    List<AlertHistory> findByProjectIdOrderByFiredAtDesc(String projectId, Pageable pageable);

    @Query("SELECT a FROM AlertHistory a WHERE a.projectId = :projectId " +
           "AND a.firedAt >= :since ORDER BY a.firedAt DESC")
    List<AlertHistory> findByProjectIdSince(
            @Param("projectId") String projectId,
            @Param("since") Instant since);

    @Query("SELECT a FROM AlertHistory a WHERE a.firedAt >= :since ORDER BY a.firedAt DESC")
    List<AlertHistory> findAllSince(@Param("since") Instant since);

    long countByProjectIdAndSeverityAndFiredAtAfter(
            String projectId, String severity, Instant after);
}
