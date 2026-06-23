package com.platform.core.service;

import com.platform.core.domain.*;
import com.platform.core.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Creates manual test runs from curated test cases — Kiwi-style.
 *
 * <p>Enforces the "confirmed gate": only {@code APPROVED} test cases may enter a
 * run (mirrors Kiwi's DRAFT→CONFIRMED requirement). Each case's properties expand
 * into one {@link TestCaseExecution} per value combination (full or pairwise),
 * with the chosen values recorded as {@link TestExecutionProperty} rows.</p>
 */
@Service
public class TcmRunService {

    private static final Logger log = LoggerFactory.getLogger(TcmRunService.class);
    private static final String APPROVED = "APPROVED";

    private final TestRunRepository runRepo;
    private final TestCaseExecutionRepository executionRepo;
    private final PlatformTestCaseRepository testCaseRepo;
    private final TestCasePropertyRepository casePropertyRepo;
    private final TestExecutionPropertyRepository executionPropertyRepo;
    private final EnvironmentRepository environmentRepo;
    private final PropertyMatrixService matrixService;

    public TcmRunService(TestRunRepository runRepo,
                         TestCaseExecutionRepository executionRepo,
                         PlatformTestCaseRepository testCaseRepo,
                         TestCasePropertyRepository casePropertyRepo,
                         TestExecutionPropertyRepository executionPropertyRepo,
                         EnvironmentRepository environmentRepo,
                         PropertyMatrixService matrixService) {
        this.runRepo               = runRepo;
        this.executionRepo         = executionRepo;
        this.testCaseRepo          = testCaseRepo;
        this.casePropertyRepo      = casePropertyRepo;
        this.executionPropertyRepo = executionPropertyRepo;
        this.environmentRepo       = environmentRepo;
        this.matrixService         = matrixService;
    }

    /**
     * Creates a run for the given approved test cases, expanding each case's
     * property matrix into executions.
     *
     * @throws IllegalArgumentException if any requested case is missing, belongs to
     *         another project, or is not {@code APPROVED}.
     */
    @Transactional
    public TestRun createRun(UUID projectId, String name, String releaseVersion,
                             UUID environmentId, List<UUID> caseIds,
                             MatrixType matrixType, String triggeredBy) {
        return createRun(projectId, name, releaseVersion, environmentId, caseIds,
                matrixType, triggeredBy, null, null, null, null);
    }

    /** As above, with run-level monitoring dimensions (release / iteration / area / team). */
    @Transactional
    public TestRun createRun(UUID projectId, String name, String releaseVersion,
                             UUID environmentId, List<UUID> caseIds,
                             MatrixType matrixType, String triggeredBy,
                             UUID releaseId, String iterationPath, String areaPath, UUID teamId) {

        if (caseIds == null || caseIds.isEmpty()) {
            throw new IllegalArgumentException("A test run requires at least one test case");
        }

        // ── Confirmed gate ────────────────────────────────────────────────────
        List<PlatformTestCase> cases = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        for (UUID caseId : caseIds) {
            Optional<PlatformTestCase> tc = testCaseRepo.findById(caseId);
            if (tc.isEmpty() || !projectId.equals(tc.get().getProjectId())) {
                rejected.add(caseId + " (not found in project)");
            } else if (!APPROVED.equals(tc.get().getStatus())) {
                rejected.add(caseId + " (status=" + tc.get().getStatus() + ", must be APPROVED)");
            } else {
                cases.add(tc.get());
            }
        }
        if (!rejected.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot create run — these cases are not eligible: " + String.join("; ", rejected));
        }

        // ── Environment ───────────────────────────────────────────────────────
        String envName = "STAGING";
        if (environmentId != null) {
            Environment env = environmentRepo.findById(environmentId).orElseThrow(() ->
                    new IllegalArgumentException("Environment not found: " + environmentId));
            if (!projectId.equals(env.getProjectId())) {
                throw new IllegalArgumentException("Environment belongs to a different project");
            }
            envName = env.getName();
        }

        TestRun run = new TestRun(projectId, name, releaseVersion, envName, triggeredBy);
        run.setEnvironmentId(environmentId);
        run.setDimensions(releaseId, iterationPath, areaPath, teamId);
        run.setStartedAt(java.time.Instant.now());
        run = runRepo.save(run);

        // ── Expand each case into executions ───────────────────────────────────
        int total = 0;
        for (PlatformTestCase tc : cases) {
            LinkedHashMap<String, List<String>> axes = loadAxes(tc.getId());
            List<LinkedHashMap<String, String>> combos = matrixService.expand(axes, matrixType);

            if (combos.isEmpty()) {
                executionRepo.save(new TestCaseExecution(run.getId(), tc.getId()));
                total++;
            } else {
                for (LinkedHashMap<String, String> combo : combos) {
                    String comboKey = PropertyMatrixService.serialize(combo);
                    TestCaseExecution exec = executionRepo.save(
                            new TestCaseExecution(run.getId(), tc.getId(), comboKey));
                    combo.forEach((k, v) -> executionPropertyRepo.save(
                            new TestExecutionProperty(exec.getId(), k, v)));
                    total++;
                }
            }
        }

        log.info("[TCM] Created run {} for project {} — {} case(s) → {} execution(s) ({})",
                run.getId(), projectId, cases.size(), total, matrixType);
        return run;
    }

    /** Groups a case's properties into name → ordered distinct values. */
    private LinkedHashMap<String, List<String>> loadAxes(UUID testCaseId) {
        LinkedHashMap<String, List<String>> axes = new LinkedHashMap<>();
        for (TestCaseProperty p : casePropertyRepo.findByTestCaseId(testCaseId)) {
            axes.computeIfAbsent(p.getName(), k -> new ArrayList<>());
            List<String> values = axes.get(p.getName());
            if (!values.contains(p.getValue())) values.add(p.getValue());
        }
        return axes;
    }
}
