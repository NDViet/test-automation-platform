package com.platform.core.repository;

import com.platform.core.domain.AuditEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findByTeamIdOrderByOccurredAtDesc(UUID teamId, Pageable pageable);

    @Query("SELECT a FROM AuditEvent a WHERE a.occurredAt >= :since ORDER BY a.occurredAt DESC")
    List<AuditEvent> findAllSince(@Param("since") Instant since, Pageable pageable);

    @Query("SELECT a FROM AuditEvent a WHERE a.eventType = :type " +
           "AND a.occurredAt >= :since ORDER BY a.occurredAt DESC")
    List<AuditEvent> findByEventTypeSince(
            @Param("type") String type,
            @Param("since") Instant since,
            Pageable pageable);

    List<AuditEvent> findByActorKeyIdOrderByOccurredAtDesc(UUID keyId, Pageable pageable);
}
