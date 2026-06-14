package com.platform.ingestion.management.tcm;

import com.platform.core.domain.PlatformTestCase;
import com.platform.core.domain.TestCaseExecution;
import com.platform.core.domain.TestRun;
import com.platform.core.repository.PlatformTestCaseRepository;
import com.platform.core.repository.TestCaseExecutionRepository;
import com.platform.core.repository.TestRunRepository;
import com.platform.core.service.MatrixType;
import com.platform.core.service.TcmRunService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class TestRunService {

    private final TestRunRepository runRepo;
    private final TestCaseExecutionRepository execRepo;
    private final PlatformTestCaseRepository testCaseRepo;
    private final TcmRunService tcmRunService;

    public TestRunService(TestRunRepository runRepo,
                          TestCaseExecutionRepository execRepo,
                          PlatformTestCaseRepository testCaseRepo,
                          TcmRunService tcmRunService) {
        this.runRepo        = runRepo;
        this.execRepo       = execRepo;
        this.testCaseRepo   = testCaseRepo;
        this.tcmRunService  = tcmRunService;
    }

    @Transactional(readOnly = true)
    public List<TestRunDto> list(UUID projectId) {
        return runRepo.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(run -> toDto(run))
                .toList();
    }

    @Transactional(readOnly = true)
    public TestRunDto get(UUID projectId, UUID runId) {
        TestRun run = loadAndVerify(projectId, runId);
        return toDto(run);
    }

    public TestRunDto create(UUID projectId, CreateTestRunRequest req) {
        // Non-empty runs go through TcmRunService: enforces the confirmed gate
        // (APPROVED-only) and expands each case's property matrix (Full/Pairwise)
        // into per-combination executions under the chosen Environment.
        if (req.testCaseIds() != null && !req.testCaseIds().isEmpty()) {
            List<UUID> ids = req.testCaseIds().stream().map(UUID::fromString).toList();
            UUID environmentId = (req.environmentId() != null && !req.environmentId().isBlank())
                    ? UUID.fromString(req.environmentId()) : null;
            MatrixType matrixType = parseMatrix(req.matrixType());
            try {
                TestRun run = tcmRunService.createRun(projectId, req.name(), req.releaseVersion(),
                        environmentId, ids, matrixType, req.triggeredBy());
                return toDto(run);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
            }
        }

        // Empty run (cases added later).
        TestRun run = new TestRun(projectId, req.name(), req.releaseVersion(),
                req.environment(), req.triggeredBy());
        run.setStartedAt(Instant.now());
        run = runRepo.save(run);
        return toDto(run);
    }

    private MatrixType parseMatrix(String value) {
        if (value == null || value.isBlank()) return MatrixType.FULL;
        try {
            return MatrixType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid matrixType: " + value);
        }
    }

    public TestRunDto complete(UUID projectId, UUID runId) {
        TestRun run = loadAndVerify(projectId, runId);
        run.complete();
        run = runRepo.save(run);
        return toDto(run);
    }

    public void delete(UUID projectId, UUID runId) {
        TestRun run = loadAndVerify(projectId, runId);
        List<TestCaseExecution> executions = execRepo.findByTestRunId(runId);
        execRepo.deleteAll(executions);
        runRepo.delete(run);
    }

    @Transactional(readOnly = true)
    public List<TestCaseExecutionDto> listExecutions(UUID projectId, UUID runId) {
        loadAndVerify(projectId, runId);
        List<TestCaseExecution> executions = execRepo.findByTestRunId(runId);
        List<UUID> tcIds = executions.stream().map(TestCaseExecution::getTestCaseId).toList();
        Map<UUID, String> titleMap = testCaseRepo.findAllById(tcIds).stream()
                .collect(Collectors.toMap(PlatformTestCase::getId, PlatformTestCase::getTitle));
        return executions.stream()
                .map(e -> TestCaseExecutionDto.from(e, titleMap.get(e.getTestCaseId())))
                .toList();
    }

    public TestCaseExecutionDto updateExecution(UUID projectId, UUID runId, UUID execId,
                                                 UpdateExecutionRequest req) {
        loadAndVerify(projectId, runId);
        TestCaseExecution exec = execRepo.findById(execId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Execution not found: " + execId));
        if (!exec.getTestRunId().equals(runId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Execution not found for run: " + runId);
        }
        exec.record(req.status(), req.actualResult(), req.notes(), req.executedBy());
        exec = execRepo.save(exec);
        String title = testCaseRepo.findById(exec.getTestCaseId())
                .map(PlatformTestCase::getTitle)
                .orElse(null);
        return TestCaseExecutionDto.from(exec, title);
    }

    // --- helpers ---

    private TestRun loadAndVerify(UUID projectId, UUID runId) {
        TestRun run = runRepo.findById(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Test run not found: " + runId));
        if (!run.getProjectId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Test run not found for project: " + projectId);
        }
        return run;
    }

    private TestRunDto toDto(TestRun run) {
        List<TestCaseExecution> executions = execRepo.findByTestRunId(run.getId());
        long total   = executions.size();
        long passed  = executions.stream().filter(e -> "PASSED".equals(e.getStatus())).count();
        long failed  = executions.stream().filter(e -> "FAILED".equals(e.getStatus())).count();
        long blocked = executions.stream().filter(e -> "BLOCKED".equals(e.getStatus())).count();
        long skipped = executions.stream().filter(e -> "SKIPPED".equals(e.getStatus())).count();
        long pending = executions.stream().filter(e -> "PENDING".equals(e.getStatus())).count();
        return TestRunDto.from(run, total, passed, failed, blocked, skipped, pending);
    }
}
