package com.platform.core.repository;

import com.platform.core.domain.TestSuite;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestSuiteRepository extends JpaRepository<TestSuite, UUID> {

  List<TestSuite> findByProjectIdOrderByNameAsc(UUID projectId);

  Optional<TestSuite> findByProjectIdAndId(UUID projectId, UUID id);

  /** Root suites (no parent) for a project — entry points of the plan tree. */
  List<TestSuite> findByProjectIdAndParentIdIsNullOrderByNameAsc(UUID projectId);

  /** Direct children of a suite. */
  List<TestSuite> findByParentIdOrderByNameAsc(UUID parentId);
}
