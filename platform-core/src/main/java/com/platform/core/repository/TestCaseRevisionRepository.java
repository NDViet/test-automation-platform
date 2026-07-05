package com.platform.core.repository;

import com.platform.core.domain.TestCaseRevision;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestCaseRevisionRepository extends JpaRepository<TestCaseRevision, UUID> {

  List<TestCaseRevision> findByTestCaseIdOrderByVersionDesc(UUID testCaseId);

  int countByTestCaseId(UUID testCaseId);
}
