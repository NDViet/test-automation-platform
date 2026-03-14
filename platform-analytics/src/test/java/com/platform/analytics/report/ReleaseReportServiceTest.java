package com.platform.analytics.report;

import com.platform.analytics.trends.QualityGateEvaluator;
import com.platform.analytics.trends.QualityGateResult;
import com.platform.common.enums.SourceFormat;
import com.platform.common.enums.TestStatus;
import com.platform.core.domain.*;
import com.platform.core.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReleaseReportServiceTest {

    @Mock TestExecutionRepository executionRepo;
    @Mock TestCaseResultRepository resultRepo;
    @Mock FlakinessScoreRepository scoreRepo;
    @Mock ProjectRepository projectRepo;
    @Mock QualityGateEvaluator gateEvaluator;

    ReleaseReportService service;

    UUID projectId = UUID.randomUUID();
    Instant from   = Instant.now().minus(14, ChronoUnit.DAYS);
    Instant to     = Instant.now();

    @BeforeEach
    void setUp() {
        service = new ReleaseReportService(
                executionRepo, resultRepo, scoreRepo, projectRepo, gateEvaluator);
    }

    @Test
    void generatesReportForProjectWithRuns() {
        Project project = makeProject("proj-alpha");
        when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));

        TestExecution exec = makeExecution(project, 100, 85, 15, 0, 0);
        when(executionRepo.findByProjectAndDateRange(eq(projectId), any(), any()))
                .thenReturn(List.of(exec));
        when(scoreRepo.findTopFlakyByProject(eq(projectId), any(Pageable.class)))
                .thenReturn(List.of());
        when(resultRepo.findByExecutionIdAndStatus(any(), eq(TestStatus.FAILED)))
                .thenReturn(List.of());
        when(gateEvaluator.evaluate(any(), eq(projectId)))
                .thenReturn(QualityGateResult.pass(0.85, 15));

        ReleaseReportDto report = service.generate(projectId, from, to, "v1.0.0");

        assertThat(report.projectId()).isEqualTo(projectId);
        assertThat(report.releaseTag()).isEqualTo("v1.0.0");
        assertThat(report.totalRuns()).isEqualTo(1);
        assertThat(report.totalTests()).isEqualTo(100);
        assertThat(report.totalPassed()).isEqualTo(85);
        assertThat(report.totalFailed()).isEqualTo(15);
        assertThat(report.overallPassRate()).isEqualTo(0.85);
        assertThat(report.qualityGatePassed()).isTrue();
    }

    @Test
    void returnsEmptyReportForProjectWithNoRuns() {
        Project project = makeProject("proj-empty");
        when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));
        when(executionRepo.findByProjectAndDateRange(any(), any(), any())).thenReturn(List.of());
        when(scoreRepo.findTopFlakyByProject(any(), any())).thenReturn(List.of());

        ReleaseReportDto report = service.generate(projectId, from, to, null);

        assertThat(report.totalRuns()).isZero();
        assertThat(report.totalTests()).isZero();
        assertThat(report.overallPassRate()).isZero();
        assertThat(report.qualityGatePassed()).isTrue();
        assertThat(report.releaseTag()).isNull();
    }

    @Test
    void countsCriticalFlakyTests() {
        Project project = makeProject("proj-flaky");
        when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));
        when(executionRepo.findByProjectAndDateRange(any(), any(), any())).thenReturn(List.of());

        FlakinessScore critical = FlakinessScore.builder()
                .testId("Test#flaky1").projectId(projectId)
                .classification(FlakinessScore.Classification.CRITICAL_FLAKY)
                .score(new java.math.BigDecimal("0.75"))
                .totalRuns(20).failureCount(15)
                .failureRate(new java.math.BigDecimal("0.75"))
                .build();
        FlakinessScore stable = FlakinessScore.builder()
                .testId("Test#stable").projectId(projectId)
                .classification(FlakinessScore.Classification.STABLE)
                .score(new java.math.BigDecimal("0.01"))
                .totalRuns(20).failureCount(0)
                .failureRate(new java.math.BigDecimal("0.0"))
                .build();

        when(scoreRepo.findTopFlakyByProject(eq(projectId), any())).thenReturn(List.of(critical, stable));

        ReleaseReportDto report = service.generate(projectId, from, to, null);

        assertThat(report.criticalFlakyCount()).isEqualTo(1);
        assertThat(report.topCriticalFlakyTests()).containsExactly("Test#flaky1");
    }

    @Test
    void throwsWhenProjectNotFound() {
        when(projectRepo.findById(projectId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.generate(projectId, from, to, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project not found");
    }

    @Test
    void qualityGateViolationsIncludedInReport() {
        Project project = makeProject("proj-fail");
        when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));

        TestExecution exec = makeExecution(project, 100, 50, 50, 0, 0);
        when(executionRepo.findByProjectAndDateRange(any(), any(), any())).thenReturn(List.of(exec));
        when(scoreRepo.findTopFlakyByProject(any(), any())).thenReturn(List.of());
        when(resultRepo.findByExecutionIdAndStatus(any(), any())).thenReturn(List.of());
        when(gateEvaluator.evaluate(any(), any())).thenReturn(
                QualityGateResult.fail(0.50, 50,
                        List.of("Pass rate 50.0% is below minimum 80.0%")));

        ReleaseReportDto report = service.generate(projectId, from, to, "v2.0.0");

        assertThat(report.qualityGatePassed()).isFalse();
        assertThat(report.qualityGateViolations()).hasSize(1);
        assertThat(report.qualityGateViolations().get(0)).contains("80.0%");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Project makeProject(String slug) {
        Team team = new Team("Test Team", slug + "-team");
        return new Project(team, "Test Project", slug);
    }

    private TestExecution makeExecution(Project project, int total, int passed,
                                         int failed, int broken, int skipped) {
        return new TestExecution.Builder()
                .project(project)
                .runId(UUID.randomUUID().toString())
                .environment("ci")
                .branch("main")
                .totalTests(total).passed(passed).failed(failed)
                .broken(broken).skipped(skipped)
                .executedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .sourceFormat(SourceFormat.JUNIT_XML)
                .build();
    }
}
