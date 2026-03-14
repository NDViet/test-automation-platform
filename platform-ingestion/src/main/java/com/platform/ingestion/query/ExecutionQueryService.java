package com.platform.ingestion.query;

import com.platform.core.repository.TestCaseResultRepository;
import com.platform.core.repository.TestExecutionRepository;
import com.platform.ingestion.query.dto.ExecutionDetailDto;
import com.platform.ingestion.query.dto.ExecutionSummaryDto;
import com.platform.ingestion.query.dto.TestCaseDto;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ExecutionQueryService {

    private final TestExecutionRepository executionRepo;
    private final TestCaseResultRepository testCaseRepo;

    public ExecutionQueryService(TestExecutionRepository executionRepo,
                                  TestCaseResultRepository testCaseRepo) {
        this.executionRepo = executionRepo;
        this.testCaseRepo  = testCaseRepo;
    }

    public List<ExecutionSummaryDto> findByProject(UUID projectId, int limit) {
        return executionRepo
                .findByProjectIdOrderByExecutedAtDesc(projectId, PageRequest.of(0, limit))
                .stream()
                .map(ExecutionSummaryDto::from)
                .toList();
    }

    public Optional<ExecutionDetailDto> findByRunId(String runId) {
        return executionRepo.findByRunId(runId).map(exec -> {
            List<TestCaseDto> cases = testCaseRepo.findByExecutionId(exec.getId())
                    .stream().map(TestCaseDto::from).toList();
            return new ExecutionDetailDto(ExecutionSummaryDto.from(exec), cases);
        });
    }
}
