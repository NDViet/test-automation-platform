package com.platform.core.repository;

import com.platform.core.domain.TestExecutionProperty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TestExecutionPropertyRepository extends JpaRepository<TestExecutionProperty, UUID> {
    List<TestExecutionProperty> findByTestCaseExecutionId(UUID testCaseExecutionId);
}
