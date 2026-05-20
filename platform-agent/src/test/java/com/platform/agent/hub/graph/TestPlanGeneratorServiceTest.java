package com.platform.agent.hub.graph;

import com.platform.agent.contract.AgentGridFixtures;
import com.platform.core.domain.*;
import com.platform.core.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TestPlanGeneratorService.
 * Verifies coverage score computation, risk level derivation, and plan item creation.
 */
@ExtendWith(MockitoExtension.class)
class TestPlanGeneratorServiceTest {

    @Mock private SotReleaseRepository            releaseRepo;
    @Mock private SotTestPlanRepository           planRepo;
    @Mock private SotTestPlanItemRepository       itemRepo;
    @Mock private SotReleaseRequirementRepository releaseReqRepo;
    @Mock private PlatformTestCaseRepository      testCaseRepo;
    @Mock private GraphService                    graphService;

    private TestPlanGeneratorService service;

    private final UUID projectId = AgentGridFixtures.PROJECT_ID;
    private final UUID releaseId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final UUID req1Id    = UUID.randomUUID();
    private final UUID req2Id    = UUID.randomUUID();
    private final UUID tc1Id     = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TestPlanGeneratorService(
                releaseRepo, planRepo, itemRepo, releaseReqRepo, testCaseRepo, graphService);
    }

    @Test
    void generateForRelease_noRequirements_coverageZeroRiskLow() {
        setupRelease();
        SotTestPlan plan = new SotTestPlan(projectId, releaseId);
        when(planRepo.findByProjectIdAndReleaseId(projectId, releaseId)).thenReturn(Optional.empty());
        when(planRepo.save(any(SotTestPlan.class))).thenReturn(plan);
        when(releaseReqRepo.findRequirementIdsByReleaseId(releaseId)).thenReturn(List.of());
        when(itemRepo.findByPlanId(any())).thenReturn(List.of());

        SotTestPlan result = service.generateForRelease(projectId, releaseId);

        assertThat(result.getCoverageScore()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(itemRepo, never()).save(any());
    }

    @Test
    void generateForRelease_allRequirementsCovered_scoreOneRiskLow() {
        setupRelease();
        SotTestPlan plan = setupPlan();
        when(releaseReqRepo.findRequirementIdsByReleaseId(releaseId)).thenReturn(List.of(req1Id, req2Id));

        PlatformTestCase tc1 = makeTc(tc1Id, false);
        when(graphService.getTestCases(projectId, req1Id)).thenReturn(List.of(tc1));
        when(graphService.getTestCases(projectId, req2Id)).thenReturn(List.of(tc1));
        when(testCaseRepo.findById(tc1Id)).thenReturn(Optional.of(tc1));
        when(itemRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SotTestPlan result = service.generateForRelease(projectId, releaseId);

        assertThat(result.getCoverageScore()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(result.getRiskLevel()).isEqualTo("LOW");
    }

    @Test
    void generateForRelease_halfCovered_scoreFiftyPercentRiskHigh() {
        setupRelease();
        SotTestPlan plan = setupPlan();
        when(releaseReqRepo.findRequirementIdsByReleaseId(releaseId)).thenReturn(List.of(req1Id, req2Id));

        PlatformTestCase tc1 = makeTc(tc1Id, false);
        when(graphService.getTestCases(projectId, req1Id)).thenReturn(List.of(tc1));
        when(graphService.getTestCases(projectId, req2Id)).thenReturn(List.of()); // uncovered
        when(testCaseRepo.findById(tc1Id)).thenReturn(Optional.of(tc1));
        when(itemRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SotTestPlan result = service.generateForRelease(projectId, releaseId);

        assertThat(result.getCoverageScore()).isEqualByComparingTo(new BigDecimal("0.500"));
        assertThat(result.getRiskLevel()).isEqualTo("HIGH");
    }

    @Test
    void generateForRelease_tcCoveringMultipleReqs_getsHigherPriority() {
        setupRelease();
        SotTestPlan plan = setupPlan();
        // tc1 covers both req1 and req2 and req3 → MUST_RUN
        UUID req3Id = UUID.randomUUID();
        when(releaseReqRepo.findRequirementIdsByReleaseId(releaseId))
                .thenReturn(List.of(req1Id, req2Id, req3Id));

        PlatformTestCase tc1 = makeTc(tc1Id, true);
        when(graphService.getTestCases(projectId, req1Id)).thenReturn(List.of(tc1));
        when(graphService.getTestCases(projectId, req2Id)).thenReturn(List.of(tc1));
        when(graphService.getTestCases(projectId, req3Id)).thenReturn(List.of(tc1));
        when(testCaseRepo.findById(tc1Id)).thenReturn(Optional.of(tc1));

        @SuppressWarnings("unchecked")
        var itemCaptor = org.mockito.ArgumentCaptor.forClass(SotTestPlanItem.class);
        when(itemRepo.save(itemCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.generateForRelease(projectId, releaseId);

        SotTestPlanItem capturedItem = itemCaptor.getValue();
        assertThat(capturedItem.getPriority()).isEqualTo("MUST_RUN");
        assertThat(capturedItem.getExecutionType()).isEqualTo("AUTOMATED");
    }

    @Test
    void generateForRelease_existingPlanItems_areReplacedOnRegenerate() {
        setupRelease();
        SotTestPlan existingPlan = new SotTestPlan(projectId, releaseId);
        setField(existingPlan, "id", UUID.randomUUID());
        when(planRepo.findByProjectIdAndReleaseId(projectId, releaseId))
                .thenReturn(Optional.of(existingPlan));
        when(planRepo.save(any())).thenReturn(existingPlan);

        SotTestPlanItem oldItem = new SotTestPlanItem(existingPlan.getId(), UUID.randomUUID(),
                List.of(req1Id), "MANUAL", "SHOULD_RUN");
        when(itemRepo.findByPlanId(existingPlan.getId())).thenReturn(List.of(oldItem));
        when(releaseReqRepo.findRequirementIdsByReleaseId(releaseId)).thenReturn(List.of());

        service.generateForRelease(projectId, releaseId);

        verify(itemRepo).delete(oldItem);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void setupRelease() {
        SotRelease release = new SotRelease(projectId, "v2.0", "VERSION", "FV-1");
        setField(release, "id", releaseId);
        when(releaseRepo.findById(releaseId)).thenReturn(Optional.of(release));
    }

    private SotTestPlan setupPlan() {
        SotTestPlan plan = new SotTestPlan(projectId, releaseId);
        UUID planId = UUID.randomUUID();
        setField(plan, "id", planId);
        when(planRepo.findByProjectIdAndReleaseId(projectId, releaseId)).thenReturn(Optional.empty());
        when(planRepo.save(any(SotTestPlan.class))).thenReturn(plan);
        when(itemRepo.findByPlanId(planId)).thenReturn(List.of());
        return plan;
    }

    private PlatformTestCase makeTc(UUID id, boolean hasAutomation) {
        PlatformTestCase tc = new PlatformTestCase(
                projectId, "Test", List.of(), "AGENT", null);
        setField(tc, "id", id);
        if (hasAutomation) tc.setHasAutomation(true);
        return tc;
    }

    private void setField(Object obj, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = findField(obj.getClass(), fieldName);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) {
            throw new RuntimeException("Cannot set field " + fieldName, e);
        }
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String name) {
        Class<?> c = clazz;
        while (c != null) {
            try { return c.getDeclaredField(name); }
            catch (NoSuchFieldException e) { c = c.getSuperclass(); }
        }
        throw new RuntimeException("Field not found: " + name);
    }
}
