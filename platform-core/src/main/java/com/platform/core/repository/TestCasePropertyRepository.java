package com.platform.core.repository;

import com.platform.core.domain.TestCaseProperty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TestCasePropertyRepository extends JpaRepository<TestCaseProperty, UUID> {
    List<TestCaseProperty> findByTestCaseId(UUID testCaseId);
}
