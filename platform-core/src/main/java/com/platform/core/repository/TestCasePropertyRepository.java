package com.platform.core.repository;

import com.platform.core.domain.TestCaseProperty;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestCasePropertyRepository extends JpaRepository<TestCaseProperty, UUID> {
  List<TestCaseProperty> findByTestCaseId(UUID testCaseId);
}
