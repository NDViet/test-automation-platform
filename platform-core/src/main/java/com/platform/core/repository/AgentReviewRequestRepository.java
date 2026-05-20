package com.platform.core.repository;

import com.platform.core.domain.AgentReviewRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AgentReviewRequestRepository extends JpaRepository<AgentReviewRequest, UUID> {
    List<AgentReviewRequest> findByWorkflowIdAndStatus(UUID workflowId, String status);

    @Query("SELECT r FROM AgentReviewRequest r WHERE r.status = 'PENDING' AND r.expiresAt < :now")
    List<AgentReviewRequest> findExpired(Instant now);
}
