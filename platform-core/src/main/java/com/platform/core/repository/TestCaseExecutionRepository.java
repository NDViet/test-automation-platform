package com.platform.core.repository;

import com.platform.core.domain.TestCaseExecution;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TestCaseExecutionRepository extends JpaRepository<TestCaseExecution, UUID> {

  List<TestCaseExecution> findByTestRunId(UUID testRunId);

  Optional<TestCaseExecution> findByTestRunIdAndTestCaseId(UUID testRunId, UUID testCaseId);

  long countByTestRunIdAndStatus(UUID testRunId, String status);

  long countByTestRunId(UUID testRunId);

  /** Returns [testRunId, status, count] rows for all given run IDs — one batch query. */
  @Query(
      "SELECT e.testRunId, e.status, COUNT(e) FROM TestCaseExecution e WHERE e.testRunId IN :runIds"
          + " GROUP BY e.testRunId, e.status")
  List<Object[]> countByRunIdsGrouped(@Param("runIds") Collection<UUID> runIds);
}
