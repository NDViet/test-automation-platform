package com.platform.core.repository;

import com.platform.core.domain.TestCaseStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TestCaseStepRepository extends JpaRepository<TestCaseStep, UUID> {

    List<TestCaseStep> findByTestCaseIdOrderByStepNumberAsc(UUID testCaseId);

    void deleteAllByTestCaseId(UUID testCaseId);
}
