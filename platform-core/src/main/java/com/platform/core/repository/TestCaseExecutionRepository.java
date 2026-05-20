package com.platform.core.repository;

import com.platform.core.domain.TestCaseExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TestCaseExecutionRepository extends JpaRepository<TestCaseExecution, UUID> {

    List<TestCaseExecution> findByTestRunId(UUID testRunId);

    Optional<TestCaseExecution> findByTestRunIdAndTestCaseId(UUID testRunId, UUID testCaseId);

    long countByTestRunIdAndStatus(UUID testRunId, String status);

    long countByTestRunId(UUID testRunId);
}
