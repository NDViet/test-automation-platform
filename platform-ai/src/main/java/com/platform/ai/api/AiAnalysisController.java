package com.platform.ai.api;

import com.platform.ai.api.dto.FailureAnalysisDto;
import com.platform.ai.classification.FailureClassificationService;
import com.platform.common.enums.TestStatus;
import com.platform.core.domain.FailureAnalysis;
import com.platform.core.domain.TestCaseResult;
import com.platform.core.repository.FailureAnalysisRepository;
import com.platform.core.repository.TestCaseResultRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * REST API for querying and triggering AI failure analysis.
 *
 * <pre>
 * GET  /api/v1/projects/{projectId}/analyses                   — list recent analyses
 * GET  /api/v1/projects/{projectId}/analyses?category=X        — filter by category
 * GET  /api/v1/projects/{projectId}/tests/{testId}/analysis    — latest for a test
 * POST /api/v1/projects/{projectId}/results/{resultId}/analyse — on-demand classify
 * </pre>
 */
@RestController
@RequestMapping("/api/v1")
public class AiAnalysisController {

    private final FailureAnalysisRepository analysisRepo;
    private final TestCaseResultRepository resultRepo;
    private final FailureClassificationService classificationService;

    public AiAnalysisController(FailureAnalysisRepository analysisRepo,
                                 TestCaseResultRepository resultRepo,
                                 FailureClassificationService classificationService) {
        this.analysisRepo           = analysisRepo;
        this.resultRepo             = resultRepo;
        this.classificationService  = classificationService;
    }

    /**
     * List recent failure analyses for a project (default: last 7 days).
     *
     * @param projectId UUID of the project
     * @param category  optional filter (APPLICATION_BUG, TEST_DEFECT, …)
     * @param days      look-back window in days (default 7)
     */
    @GetMapping("/projects/{projectId}/analyses")
    public List<FailureAnalysisDto> listAnalyses(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "7") int days) {

        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        List<FailureAnalysis> results;
        if (category != null && !category.isBlank()) {
            results = analysisRepo.findByProjectIdAndCategory(
                    projectId, category.toUpperCase(), PageRequest.of(0, 200));
        } else {
            results = analysisRepo.findByProjectIdSince(projectId, since);
        }

        return results.stream().map(FailureAnalysisDto::from).toList();
    }

    /**
     * Latest analysis for a specific test in a project.
     */
    @GetMapping("/projects/{projectId}/tests/{testId}/analysis")
    public ResponseEntity<FailureAnalysisDto> getLatestForTest(
            @PathVariable UUID projectId,
            @PathVariable String testId) {

        return analysisRepo
                .findTopByTestIdAndProjectIdOrderByAnalysedAtDesc(testId, projectId)
                .map(FailureAnalysisDto::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Analysis history for a specific test (paginated, newest first).
     */
    @GetMapping("/projects/{projectId}/tests/{testId}/analyses")
    public List<FailureAnalysisDto> getHistoryForTest(
            @PathVariable UUID projectId,
            @PathVariable String testId,
            @RequestParam(defaultValue = "10") int limit) {

        return analysisRepo.findByTestIdAndProjectIdOrderByAnalysedAtDesc(
                        testId, projectId, PageRequest.of(0, limit))
                .stream()
                .map(FailureAnalysisDto::from)
                .toList();
    }

    /**
     * On-demand classification for a specific TestCaseResult.
     * Useful for manual re-analysis or CI integration.
     */
    @PostMapping("/projects/{projectId}/results/{resultId}/analyse")
    public ResponseEntity<FailureAnalysisDto> analyseResult(
            @PathVariable UUID projectId,
            @PathVariable UUID resultId) {

        TestCaseResult result = resultRepo.findById(resultId)
                .orElse(null);

        if (result == null) {
            return ResponseEntity.notFound().build();
        }

        if (result.getStatus() != TestStatus.FAILED) {
            return ResponseEntity.badRequest().build();
        }

        FailureAnalysis analysis = classificationService.classify(result, projectId);
        return ResponseEntity.ok(FailureAnalysisDto.from(analysis));
    }
}
