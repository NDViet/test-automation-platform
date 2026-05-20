package com.platform.core.repository;

import com.platform.core.domain.TestRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TestRunRepository extends JpaRepository<TestRun, UUID> {

    List<TestRun> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<TestRun> findByProjectIdAndStatus(UUID projectId, String status);

    long countByProjectIdAndStatus(UUID projectId, String status);
}
