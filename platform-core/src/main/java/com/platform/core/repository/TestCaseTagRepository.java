package com.platform.core.repository;

import com.platform.core.domain.TestCaseTag;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TestCaseTagRepository extends JpaRepository<TestCaseTag, UUID> {

  List<TestCaseTag> findByTestCaseIdOrderByNameAsc(UUID testCaseId);

  Optional<TestCaseTag> findByTestCaseIdAndName(UUID testCaseId, String name);

  /** Distinct tag names used across a project's test cases — for typeahead suggestions. */
  @Query(
      """
      SELECT DISTINCT t.name FROM TestCaseTag t
      WHERE t.testCaseId IN (SELECT tc.id FROM PlatformTestCase tc WHERE tc.projectId = :projectId)
      ORDER BY t.name ASC
      """)
  List<String> findDistinctNamesByProjectId(@Param("projectId") UUID projectId);
}
