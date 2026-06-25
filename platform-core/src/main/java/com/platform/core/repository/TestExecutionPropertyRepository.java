package com.platform.core.repository;

import com.platform.core.domain.TestExecutionProperty;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestExecutionPropertyRepository
    extends JpaRepository<TestExecutionProperty, UUID> {
  List<TestExecutionProperty> findByTestCaseExecutionId(UUID testCaseExecutionId);
}
