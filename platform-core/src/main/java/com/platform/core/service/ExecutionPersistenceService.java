package com.platform.core.service;

import com.platform.common.dto.TestCaseResultDto;
import com.platform.common.dto.UnifiedTestResult;
import com.platform.core.domain.Project;
import com.platform.core.domain.Team;
import com.platform.core.domain.TestCaseResult;
import com.platform.core.domain.TestExecution;
import com.platform.core.repository.ProjectRepository;
import com.platform.core.repository.TeamRepository;
import com.platform.core.repository.TestCaseResultRepository;
import com.platform.core.repository.TestExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionPersistenceService.class);

    private final TestExecutionRepository executionRepository;
    private final TestCaseResultRepository testCaseResultRepository;
    private final TeamRepository teamRepository;
    private final ProjectRepository projectRepository;

    public ExecutionPersistenceService(
            TestExecutionRepository executionRepository,
            TestCaseResultRepository testCaseResultRepository,
            TeamRepository teamRepository,
            ProjectRepository projectRepository) {
        this.executionRepository = executionRepository;
        this.testCaseResultRepository = testCaseResultRepository;
        this.teamRepository = teamRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public TestExecution persist(UnifiedTestResult result) {
        if (executionRepository.existsByRunId(result.runId())) {
            log.warn("Duplicate run detected runId={} — skipping persistence", result.runId());
            return executionRepository.findByRunId(result.runId()).orElseThrow();
        }

        var team    = findOrCreateTeam(result.teamId());
        var project = findOrCreateProject(team, result.projectId());

        var execution = buildExecution(result, project);
        execution = executionRepository.save(execution);

        persistTestCases(execution, result);

        log.info("Persisted execution runId={} teamId={} projectId={} total={} failed={}",
                result.runId(), result.teamId(), result.projectId(),
                result.total(), result.failed());

        return execution;
    }

    // ── Auto-registration ─────────────────────────────────────────────────────

    /**
     * Returns the team with the given slug, creating it on first encounter.
     * The catch handles the rare race where two concurrent results register the
     * same new team — the loser re-fetches the winner's row.
     */
    private Team findOrCreateTeam(String slug) {
        return teamRepository.findBySlug(slug).orElseGet(() -> {
            log.info("[Platform] Auto-registering new team slug={}", slug);
            try {
                return teamRepository.save(new Team(toDisplayName(slug), slug));
            } catch (DataIntegrityViolationException e) {
                return teamRepository.findBySlug(slug).orElseThrow();
            }
        });
    }

    private Project findOrCreateProject(Team team, String slug) {
        return projectRepository.findByTeamIdAndSlug(team.getId(), slug).orElseGet(() -> {
            log.info("[Platform] Auto-registering new project slug={} teamSlug={}", slug, team.getSlug());
            try {
                return projectRepository.save(new Project(team, toDisplayName(slug), slug));
            } catch (DataIntegrityViolationException e) {
                return projectRepository.findByTeamIdAndSlug(team.getId(), slug).orElseThrow();
            }
        });
    }

    /** "team-saucedemo" → "Team Saucedemo",  "proj-the-internet" → "Proj The Internet" */
    private static String toDisplayName(String slug) {
        StringBuilder sb = new StringBuilder();
        for (String word : slug.split("[-_]")) {
            if (word.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0)));
            sb.append(word.substring(1));
        }
        return sb.toString();
    }

    private TestExecution buildExecution(UnifiedTestResult r, Project project) {
        return TestExecution.builder()
                .runId(r.runId())
                .project(project)
                .branch(r.branch())
                .commitSha(r.commitSha())
                .environment(r.environment())
                .triggerType(r.triggerType())
                .sourceFormat(r.sourceFormat())
                .ciProvider(r.ciProvider())
                .ciRunUrl(r.ciRunUrl())
                .totalTests(r.total())
                .passed(r.passed())
                .failed(r.failed())
                .skipped(r.skipped())
                .broken(r.broken())
                .durationMs(r.durationMs())
                .executionMode(r.executionMode())
                .parallelism(r.parallelism())
                .suiteName(r.suiteName())
                .executedAt(r.executedAt())
                .build();
    }

    private void persistTestCases(TestExecution execution, UnifiedTestResult result) {
        for (TestCaseResultDto dto : result.testCases()) {
            var tcr = TestCaseResult.builder()
                    .execution(execution)
                    .testId(dto.testId())
                    .displayName(dto.displayName())
                    .className(dto.className())
                    .methodName(dto.methodName())
                    .tags(dto.tags())
                    .status(dto.status())
                    .durationMs(dto.durationMs())
                    .failureMessage(dto.failureMessage())
                    .stackTrace(dto.stackTrace())
                    .retryCount(dto.retryCount())
                    .build();
            testCaseResultRepository.save(tcr);
        }
    }
}
