package com.platform.ingestion.management.tcm;

import com.platform.core.domain.PlatformRequirement;
import com.platform.core.domain.PlatformTestCase;
import com.platform.core.repository.PlatformRequirementRepository;
import com.platform.core.repository.PlatformTestCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CoverageServiceTest {

    private PlatformRequirementRepository reqRepo;
    private PlatformTestCaseRepository tcRepo;
    private CoverageService service;

    private final UUID projectId = UUID.randomUUID();
    private final UUID reqAuto = UUID.randomUUID();
    private final UUID reqManual = UUID.randomUUID();
    private final UUID reqUncovered = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reqRepo = mock(PlatformRequirementRepository.class);
        tcRepo = mock(PlatformTestCaseRepository.class);
        service = new CoverageService(reqRepo, tcRepo);
    }

    private PlatformRequirement req(UUID id, String key) {
        PlatformRequirement r = mock(PlatformRequirement.class);
        when(r.getId()).thenReturn(id);
        when(r.getExternalId()).thenReturn(key);
        when(r.getTitle()).thenReturn("title-" + key);
        when(r.getIssueType()).thenReturn("STORY");
        when(r.getStatus()).thenReturn("OPEN");
        return r;
    }

    private PlatformTestCase tc(UUID reqId, boolean automated, String lastResult) {
        PlatformTestCase t = mock(PlatformTestCase.class);
        when(t.getLinkedRequirementIds()).thenReturn(List.of(reqId.toString()));
        when(t.getSourceRequirementId()).thenReturn(null);
        when(t.isHasAutomation()).thenReturn(automated);
        when(t.getLastResult()).thenReturn(lastResult);
        when(t.getLastExecutedAt()).thenReturn(Instant.now());
        return t;
    }

    @Test
    void computesCoverageBuckets() {
        PlatformRequirement a = req(reqAuto, "PAY-1");
        PlatformRequirement m = req(reqManual, "PAY-2");
        PlatformRequirement u = req(reqUncovered, "PAY-3");
        when(reqRepo.findByProjectIdOrderByUpdatedAtDesc(projectId)).thenReturn(List.of(a, m, u));

        PlatformTestCase autoCase = tc(reqAuto, true, "PASSED");
        PlatformTestCase manualCase = tc(reqManual, false, "FAILED");
        when(tcRepo.findByProjectId(projectId)).thenReturn(List.of(autoCase, manualCase));

        CoverageDto cov = service.coverage(projectId);

        assertThat(cov.totalRequirements()).isEqualTo(3);
        assertThat(cov.coveredByAutomation()).isEqualTo(1);
        assertThat(cov.coveredManualOnly()).isEqualTo(1);
        assertThat(cov.uncovered()).isEqualTo(1);
        assertThat(cov.automationCoveragePct()).isEqualTo(33.3);

        CoverageDto.Row autoRow = cov.requirements().stream()
                .filter(r -> r.externalId().equals("PAY-1")).findFirst().orElseThrow();
        assertThat(autoRow.automatedCases()).isEqualTo(1);
        assertThat(autoRow.lastStatus()).isEqualTo("PASSED");

        CoverageDto.Row gapRow = cov.requirements().stream()
                .filter(r -> r.externalId().equals("PAY-3")).findFirst().orElseThrow();
        assertThat(gapRow.automatedCases()).isZero();
        assertThat(gapRow.manualCases()).isZero();
        assertThat(gapRow.lastStatus()).isNull();
    }

    @Test
    void emptyProject_isZeroCoverage() {
        when(reqRepo.findByProjectIdOrderByUpdatedAtDesc(projectId)).thenReturn(List.of());
        when(tcRepo.findByProjectId(projectId)).thenReturn(List.of());
        CoverageDto cov = service.coverage(projectId);
        assertThat(cov.totalRequirements()).isZero();
        assertThat(cov.automationCoveragePct()).isZero();
    }
}
