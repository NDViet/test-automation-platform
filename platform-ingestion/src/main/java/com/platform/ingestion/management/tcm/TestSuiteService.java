package com.platform.ingestion.management.tcm;

import com.platform.core.domain.TestSuite;
import com.platform.core.repository.TestSuiteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TestSuiteService {

    private final TestSuiteRepository suiteRepo;

    public TestSuiteService(TestSuiteRepository suiteRepo) {
        this.suiteRepo = suiteRepo;
    }

    @Transactional(readOnly = true)
    public List<TestSuiteDto> list(UUID projectId) {
        return suiteRepo.findByProjectIdOrderByNameAsc(projectId).stream()
                .map(TestSuiteDto::from)
                .toList();
    }

    public TestSuiteDto create(UUID projectId, CreateTestSuiteRequest req) {
        TestSuite suite = new TestSuite(projectId, req.name(), req.description());
        return TestSuiteDto.from(suiteRepo.save(suite));
    }

    public TestSuiteDto update(UUID projectId, UUID suiteId, CreateTestSuiteRequest req) {
        TestSuite suite = suiteRepo.findByProjectIdAndId(projectId, suiteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Test suite not found: " + suiteId));
        suite.setName(req.name());
        suite.setDescription(req.description());
        return TestSuiteDto.from(suiteRepo.save(suite));
    }

    public void delete(UUID projectId, UUID suiteId) {
        TestSuite suite = suiteRepo.findByProjectIdAndId(projectId, suiteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Test suite not found: " + suiteId));
        suiteRepo.delete(suite);
    }
}
