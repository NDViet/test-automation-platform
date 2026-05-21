package com.platform.ingestion.management.tcm;

import com.platform.core.domain.PlatformTestCase;
import com.platform.core.domain.TestCaseStep;
import com.platform.core.repository.PlatformTestCaseRepository;
import com.platform.core.repository.TestCaseStepRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TestCaseManagementService {

    private final PlatformTestCaseRepository testCaseRepo;
    private final TestCaseStepRepository stepRepo;

    public TestCaseManagementService(PlatformTestCaseRepository testCaseRepo,
                                     TestCaseStepRepository stepRepo) {
        this.testCaseRepo = testCaseRepo;
        this.stepRepo     = stepRepo;
    }

    @Transactional(readOnly = true)
    public List<ManagedTestCaseDto> list(UUID projectId, String status, String suiteId, String search) {
        List<PlatformTestCase> cases;

        if (search != null && !search.isBlank()) {
            cases = testCaseRepo.searchByProjectId(projectId, search.trim());
            if (status != null) {
                String s = status;
                cases = cases.stream().filter(tc -> s.equals(tc.getStatus())).toList();
            }
            if (suiteId != null) {
                UUID sid = UUID.fromString(suiteId);
                cases = cases.stream().filter(tc -> sid.equals(tc.getSuiteId())).toList();
            }
        } else if (suiteId != null && status != null) {
            cases = testCaseRepo.findByProjectIdAndStatusAndSuiteId(
                    projectId, status, UUID.fromString(suiteId));
        } else if (suiteId != null) {
            cases = testCaseRepo.findByProjectIdAndSuiteId(projectId, UUID.fromString(suiteId));
        } else if (status != null) {
            cases = testCaseRepo.findByProjectIdAndStatus(projectId, status);
        } else {
            cases = testCaseRepo.findByProjectIdOrderByCreatedAtDesc(projectId);
        }

        return cases.stream()
                .map(tc -> ManagedTestCaseDto.from(tc, loadSteps(tc.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ManagedTestCaseDto get(UUID projectId, UUID tcId) {
        PlatformTestCase tc = loadAndVerify(projectId, tcId);
        return ManagedTestCaseDto.from(tc, loadSteps(tcId));
    }

    public ManagedTestCaseDto create(UUID projectId, CreateTestCaseRequest req) {
        PlatformTestCase tc = new PlatformTestCase(projectId, req.title(), req.acRefs(), "HUMAN", null);
        if (req.description() != null)        tc.setDescription(req.description());
        if (req.preconditions() != null)      tc.setPreconditions(req.preconditions());
        if (req.expectedResult() != null)     tc.setExpectedResult(req.expectedResult());
        if (req.priority() != null)           tc.setPriority(req.priority());
        if (req.suiteId() != null)            tc.setSuiteId(UUID.fromString(req.suiteId()));
        if (req.sourceRequirementId() != null) {
            UUID reqId = UUID.fromString(req.sourceRequirementId());
            tc.setSourceRequirementId(reqId);
            tc.linkRequirement(reqId);
        }
        if (req.linkedRequirementIds() != null) {
            for (String rid : req.linkedRequirementIds()) {
                try { tc.linkRequirement(UUID.fromString(rid)); } catch (IllegalArgumentException ignored) {}
            }
        }
        tc = testCaseRepo.save(tc);

        List<TestCaseStep> steps = saveSteps(tc.getId(), req.steps());
        return ManagedTestCaseDto.from(tc, steps);
    }

    public ManagedTestCaseDto update(UUID projectId, UUID tcId, UpdateTestCaseRequest req) {
        PlatformTestCase tc = loadAndVerify(projectId, tcId);
        if (req.title() != null)              tc.setTitle(req.title());
        if (req.description() != null)        tc.setDescription(req.description());
        if (req.preconditions() != null)      tc.setPreconditions(req.preconditions());
        if (req.expectedResult() != null)     tc.setExpectedResult(req.expectedResult());
        if (req.priority() != null)           tc.setPriority(req.priority());
        if (req.suiteId() != null)            tc.setSuiteId(UUID.fromString(req.suiteId()));
        if (req.sourceRequirementId() != null) tc.setSourceRequirementId(UUID.fromString(req.sourceRequirementId()));
        if (req.acRefs() != null)             tc.setAcRefs(req.acRefs());
        tc = testCaseRepo.save(tc);

        List<TestCaseStep> steps;
        if (req.steps() != null) {
            stepRepo.deleteAllByTestCaseId(tcId);
            steps = saveSteps(tc.getId(), req.steps());
        } else {
            steps = loadSteps(tcId);
        }
        return ManagedTestCaseDto.from(tc, steps);
    }

    public void delete(UUID projectId, UUID tcId) {
        PlatformTestCase tc = loadAndVerify(projectId, tcId);
        stepRepo.deleteAllByTestCaseId(tcId);
        testCaseRepo.delete(tc);
    }

    public ManagedTestCaseDto replaceSteps(UUID projectId, UUID tcId,
                                           List<CreateTestCaseRequest.StepRequest> steps) {
        PlatformTestCase tc = loadAndVerify(projectId, tcId);
        stepRepo.deleteAllByTestCaseId(tcId);
        List<TestCaseStep> saved = saveSteps(tcId, steps);
        return ManagedTestCaseDto.from(tc, saved);
    }

    public ManagedTestCaseDto submitForReview(UUID projectId, UUID tcId) {
        PlatformTestCase tc = loadAndVerify(projectId, tcId);
        tc.submitForReview();
        tc = testCaseRepo.save(tc);
        return ManagedTestCaseDto.from(tc, loadSteps(tcId));
    }

    public ManagedTestCaseDto approve(UUID projectId, UUID tcId) {
        PlatformTestCase tc = loadAndVerify(projectId, tcId);
        tc.approve();
        tc = testCaseRepo.save(tc);
        return ManagedTestCaseDto.from(tc, loadSteps(tcId));
    }

    public ManagedTestCaseDto reject(UUID projectId, UUID tcId) {
        PlatformTestCase tc = loadAndVerify(projectId, tcId);
        tc.reject();
        tc = testCaseRepo.save(tc);
        return ManagedTestCaseDto.from(tc, loadSteps(tcId));
    }

    public ManagedTestCaseDto linkRequirement(UUID projectId, UUID tcId, UUID requirementId) {
        PlatformTestCase tc = loadAndVerify(projectId, tcId);
        tc.linkRequirement(requirementId);
        tc = testCaseRepo.save(tc);
        return ManagedTestCaseDto.from(tc, loadSteps(tcId));
    }

    public ManagedTestCaseDto unlinkRequirement(UUID projectId, UUID tcId, UUID requirementId) {
        PlatformTestCase tc = loadAndVerify(projectId, tcId);
        tc.unlinkRequirement(requirementId);
        tc = testCaseRepo.save(tc);
        return ManagedTestCaseDto.from(tc, loadSteps(tcId));
    }

    public ManagedTestCaseDto applyAnalysisSuggestion(UUID projectId, UUID tcId,
                                                        ApplySuggestionRequest req) {
        PlatformTestCase tc = loadAndVerify(projectId, tcId);
        if (req.title() != null)       tc.setTitle(req.title());
        if (req.description() != null) tc.setDescription(req.description());
        if (req.expectedResult() != null) tc.setExpectedResult(req.expectedResult());
        tc.applyAnalysisSuggestion(UUID.fromString(req.analysisId()));
        tc = testCaseRepo.save(tc);

        if (req.steps() != null && !req.steps().isEmpty()) {
            stepRepo.deleteAllByTestCaseId(tcId);
            saveSteps(tcId, req.steps());
        }
        return ManagedTestCaseDto.from(tc, loadSteps(tcId));
    }

    public ManagedTestCaseDto triggerAutomationGeneration(UUID projectId, UUID tcId, String githubConfigId) {
        PlatformTestCase tc = loadAndVerify(projectId, tcId);
        if (githubConfigId != null) {
            tc.setAutomationGithubConfigId(UUID.fromString(githubConfigId));
        }
        tc.markAutomationGenerating();
        tc = testCaseRepo.save(tc);
        return ManagedTestCaseDto.from(tc, loadSteps(tcId));
    }

    // --- helpers ---

    private PlatformTestCase loadAndVerify(UUID projectId, UUID tcId) {
        PlatformTestCase tc = testCaseRepo.findById(tcId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Test case not found: " + tcId));
        if (!tc.getProjectId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Test case not found for project: " + projectId);
        }
        return tc;
    }

    private List<TestCaseStep> loadSteps(UUID tcId) {
        return stepRepo.findByTestCaseIdOrderByStepNumberAsc(tcId);
    }

    private List<TestCaseStep> saveSteps(UUID tcId, List<CreateTestCaseRequest.StepRequest> stepRequests) {
        if (stepRequests == null || stepRequests.isEmpty()) {
            return List.of();
        }
        List<TestCaseStep> steps = new ArrayList<>();
        for (int i = 0; i < stepRequests.size(); i++) {
            CreateTestCaseRequest.StepRequest sr = stepRequests.get(i);
            steps.add(new TestCaseStep(tcId, i + 1, sr.action(), sr.expectedResult(), sr.notes()));
        }
        return stepRepo.saveAll(steps);
    }
}
