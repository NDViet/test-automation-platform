package com.platform.analytics.impact;

import com.platform.core.repository.TestCaseResultRepository;
import com.platform.core.repository.TestCoverageMappingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Test Impact Analysis REST endpoint.
 *
 * <p>Given a set of changed source files (from a git diff), returns the minimal
 * subset of tests that cover those changes plus metadata for CI decision-making.</p>
 *
 * <h3>Typical CI usage</h3>
 * <pre>{@code
 * CHANGED=$(git diff --name-only origin/main HEAD | tr '\n' ',')
 * IMPACT=$(curl -s \
 *   "http://platform:8082/api/v1/analytics/${PROJECT_ID}/impact?changedFiles=${CHANGED}" \
 *   -H "X-API-Key: ${PLATFORM_API_KEY}")
 * mvn test -Dtest=$(echo $IMPACT | jq -r '.junitFilter')
 * }</pre>
 */
@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "Test Impact Analysis", description = "Smart test selection based on changed source files")
public class TestImpactController {

    private final TestImpactService impactService;
    private final TestCoverageMappingRepository coverageRepo;
    private final TestCaseResultRepository testCaseResultRepo;

    public TestImpactController(TestImpactService impactService,
                                 TestCoverageMappingRepository coverageRepo,
                                 TestCaseResultRepository testCaseResultRepo) {
        this.impactService       = impactService;
        this.coverageRepo        = coverageRepo;
        this.testCaseResultRepo  = testCaseResultRepo;
    }

    @GetMapping("/{projectId}/impact")
    @Transactional
    @Operation(
            summary = "Analyse test impact for a set of changed files",
            description = "Returns the recommended tests to run given a list of changed source files. " +
                          "Also provides ready-to-use filter strings for Maven, Gradle, and JUnit 5."
    )
    public ResponseEntity<TestImpactResult> analyse(
            @PathVariable UUID projectId,

            @Parameter(description = "Comma-separated or repeated list of changed file paths " +
                       "(e.g. src/main/java/com/example/Foo.java)")
            @RequestParam(required = false) List<String> changedFiles,

            @Parameter(description = "Comma-separated or repeated list of fully qualified class names " +
                       "to check directly (alternative to changedFiles)")
            @RequestParam(required = false) List<String> changedClasses,

            @Parameter(description = "Git branch — stored in TIA event for filtering")
            @RequestParam(required = false) String branch,

            @Parameter(description = "Who triggered the query: api | ci | portal")
            @RequestParam(required = false, defaultValue = "api") String triggeredBy
    ) {
        long totalTests = coverageRepo.countMappedTestsByProject(projectId);

        TestImpactResult result = impactService.analyse(
                projectId, changedFiles, changedClasses, (int) totalTests,
                branch, triggeredBy);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{projectId}/impact/summary")
    @Transactional(readOnly = true)
    @Operation(summary = "Coverage summary — how many tests and classes are mapped for a project")
    public ResponseEntity<?> coverageSummary(@PathVariable UUID projectId) {
        long mappedTests   = coverageRepo.countMappedTestsByProject(projectId);
        long mappedClasses = coverageRepo.countDistinctClassesByProject(projectId);

        return ResponseEntity.ok(java.util.Map.of(
                "projectId",    projectId,
                "mappedTests",  mappedTests,
                "mappedClasses", mappedClasses,
                "tiaEnabled",   mappedTests > 0
        ));
    }
}
