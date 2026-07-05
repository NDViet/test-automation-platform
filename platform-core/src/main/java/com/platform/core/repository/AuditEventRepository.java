package com.platform.core.repository;

import com.platform.core.domain.AuditEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

  List<AuditEvent> findByTeamIdOrderByOccurredAtDesc(UUID teamId, Pageable pageable);

  @Query("SELECT a FROM AuditEvent a WHERE a.occurredAt >= :since ORDER BY a.occurredAt DESC")
  List<AuditEvent> findAllSince(@Param("since") Instant since, Pageable pageable);

  @Query(
      "SELECT a FROM AuditEvent a WHERE a.eventType = :type "
          + "AND a.occurredAt >= :since ORDER BY a.occurredAt DESC")
  List<AuditEvent> findByEventTypeSince(
      @Param("type") String type, @Param("since") Instant since, Pageable pageable);

  List<AuditEvent> findByActorKeyIdOrderByOccurredAtDesc(UUID keyId, Pageable pageable);

  /** Project-scoped audit feed with optional eventType/resourceType filters, newest first. */
  @Query(
      "SELECT a FROM AuditEvent a WHERE a.projectId = :projectId"
          + " AND (:eventType IS NULL OR a.eventType = :eventType)"
          + " AND (:resourceType IS NULL OR a.resourceType = :resourceType)"
          + " AND a.occurredAt >= :since ORDER BY a.occurredAt DESC")
  List<AuditEvent> findForProject(
      @Param("projectId") UUID projectId,
      @Param("eventType") String eventType,
      @Param("resourceType") String resourceType,
      @Param("since") Instant since,
      Pageable pageable);
}
