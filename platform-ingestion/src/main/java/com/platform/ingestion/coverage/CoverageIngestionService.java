package com.platform.ingestion.coverage;

import com.platform.common.dto.CoverageManifest;
import com.platform.common.dto.TestCaseResultDto;
import com.platform.core.domain.Project;
import com.platform.core.domain.TestCoverageMapping;
import com.platform.core.repository.ProjectRepository;
import com.platform.core.repository.TestCoverageMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists test→class coverage mappings into {@code test_coverage_mappings}.
 *
 * <p>Two ingestion paths:</p>
 * <ol>
 *   <li><b>Automatic</b> — called from {@link com.platform.ingestion.api.ResultIngestionService}
 *       when test cases carry {@code coveredClasses} (from {@code @AffectedBy} or JS SDK).</li>
 *   <li><b>Standalone manifest</b> — called from {@link CoverageIngestionController} when a
 *       team POSTs a pre-built {@link CoverageManifest} (e.g. from a JaCoCo post-processor).</li>
 * </ol>
 *
 * <p>Uses UPSERT semantics — existing mappings are touched (lastSeenAt updated),
 * new ones are inserted. This keeps the mapping table current without bloat.</p>
 */
@Service
public class CoverageIngestionService {

    private static final Logger log = LoggerFactory.getLogger(CoverageIngestionService.class);

    private final TestCoverageMappingRepository coverageRepo;
    private final ProjectRepository projectRepo;

    public CoverageIngestionService(TestCoverageMappingRepository coverageRepo,
                                    ProjectRepository projectRepo) {
        this.coverageRepo = coverageRepo;
        this.projectRepo  = projectRepo;
    }

    /**
     * Extracts coverage data from an already-parsed test run and upserts mappings.
     * No-op if no test cases carry covered-class data.
     */
    @Transactional
    public void ingestFromResults(UUID projectId, List<TestCaseResultDto> testCases) {
        if (testCases == null || testCases.isEmpty()) return;

        int count = 0;
        for (TestCaseResultDto tc : testCases) {
            if (tc.coveredClasses() == null || tc.coveredClasses().isEmpty()) continue;
            for (String className : tc.coveredClasses()) {
                if (className == null || className.isBlank()) continue;
                upsert(projectId, tc.testId(), className.trim(), null);
                count++;
            }
        }
        if (count > 0) {
            log.debug("Upserted {} coverage mappings for projectId={}", count, projectId);
        }
    }

    /**
     * Ingests a standalone {@link CoverageManifest}.
     * Returns the number of mappings upserted.
     */
    @Transactional
    public int ingestManifest(CoverageManifest manifest) {
        Optional<Project> projectOpt = projectRepo.findBySlug(manifest.projectId());
        if (projectOpt.isEmpty()) {
            log.warn("Coverage manifest submitted for unknown project slug '{}'", manifest.projectId());
            return 0;
        }
        UUID projectId = projectOpt.get().getId();

        int count = 0;
        for (CoverageManifest.Entry entry : manifest.mappings()) {
            if (entry.testId() == null || entry.coveredClasses() == null) continue;
            for (String className : entry.coveredClasses()) {
                if (className == null || className.isBlank()) continue;
                upsert(projectId, entry.testId(), className.trim(), null);
                count++;
            }
        }
        log.info("Ingested coverage manifest: projectId={} mappings={}", projectId, count);
        return count;
    }

    private void upsert(UUID projectId, String testCaseId, String className, String methodName) {
        coverageRepo.findByProjectIdAndTestCaseIdAndClassName(projectId, testCaseId, className)
                .ifPresentOrElse(
                        existing -> {
                            existing.touch();
                            coverageRepo.save(existing);
                        },
                        () -> coverageRepo.save(
                                new TestCoverageMapping(projectId, testCaseId, className, methodName))
                );
    }
}
