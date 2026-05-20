package com.platform.agent.hub.graph;

import com.platform.agent.contract.AgentGridFixtures;
import com.platform.common.agent.RequirementContext;
import com.platform.common.agent.TestGenMode;
import com.platform.common.model.EdgeType;
import com.platform.common.model.RequirementRecord;
import com.platform.core.domain.*;
import com.platform.core.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GraphService — the central traversal component.
 * Verifies the graph→context conversion contract and TestGenMode resolution.
 */
@ExtendWith(MockitoExtension.class)
class GraphServiceTest {

    @Mock private PlatformRequirementRepository requirementRepo;
    @Mock private PlatformTestCaseRepository    testCaseRepo;
    @Mock private PlatformTraceabilityEdgeRepository edgeRepo;
    @Mock private SotReleaseRepository          releaseRepo;
    @Mock private ProjectIntegrationConfigRepository configRepo;

    private GraphService graphService;

    private final UUID projectId     = AgentGridFixtures.PROJECT_ID;
    private final UUID requirementId = AgentGridFixtures.REQUIREMENT_ID;

    @BeforeEach
    void setUp() {
        graphService = new GraphService(requirementRepo, testCaseRepo, edgeRepo, releaseRepo, configRepo);
    }

    // -------------------------------------------------------------------------
    // getAncestors
    // -------------------------------------------------------------------------

    @Test
    void getAncestors_noParent_returnsEmpty() {
        PlatformRequirement req = makeRequirement(requirementId, null);
        when(requirementRepo.findById(requirementId)).thenReturn(Optional.of(req));

        List<RequirementRecord> ancestors = graphService.getAncestors(projectId, requirementId);
        assertThat(ancestors).isEmpty();
    }

    @Test
    void getAncestors_twoLevelChain_returnsParentThenGrandparent() {
        UUID parentId      = UUID.randomUUID();
        UUID grandParentId = UUID.randomUUID();

        PlatformRequirement req    = makeRequirement(requirementId, parentId);
        PlatformRequirement parent = makeRequirement(parentId, grandParentId);
        PlatformRequirement gp     = makeRequirement(grandParentId, null);

        when(requirementRepo.findById(requirementId)).thenReturn(Optional.of(req));
        when(requirementRepo.findById(parentId)).thenReturn(Optional.of(parent));
        when(requirementRepo.findById(grandParentId)).thenReturn(Optional.of(gp));

        List<RequirementRecord> ancestors = graphService.getAncestors(projectId, requirementId);

        assertThat(ancestors).hasSize(2);
        assertThat(ancestors.get(0).id()).isEqualTo(parentId);
        assertThat(ancestors.get(1).id()).isEqualTo(grandParentId);
    }

    @Test
    void getAncestors_cycleGuard_doesNotInfiniteLoop() {
        // Self-referential parent — should not loop
        PlatformRequirement req = makeRequirement(requirementId, requirementId);
        when(requirementRepo.findById(requirementId)).thenReturn(Optional.of(req));

        List<RequirementRecord> ancestors = graphService.getAncestors(projectId, requirementId);
        assertThat(ancestors).isEmpty(); // cycle detected, loop terminates
    }

    // -------------------------------------------------------------------------
    // getChildren
    // -------------------------------------------------------------------------

    @Test
    void getChildren_returnsDirectChildrenOnly() {
        PlatformRequirement child1 = makeRequirement(UUID.randomUUID(), requirementId);
        PlatformRequirement child2 = makeRequirement(UUID.randomUUID(), requirementId);
        when(requirementRepo.findByProjectIdAndParentId(projectId, requirementId))
                .thenReturn(List.of(child1, child2));
        // configId is null in makeRequirement → resolveSource returns GITHUB without querying configRepo

        List<RequirementRecord> children = graphService.getChildren(projectId, requirementId);
        assertThat(children).hasSize(2);
    }

    // -------------------------------------------------------------------------
    // hasAutomation
    // -------------------------------------------------------------------------

    @Test
    void hasAutomation_returnsTrueWhenAnyTcHasAutomation() {
        PlatformTestCase autoTc = makeTc(UUID.randomUUID(), true);
        when(testCaseRepo.findCoveringTestCases(projectId, requirementId))
                .thenReturn(List.of(autoTc));

        assertThat(graphService.hasAutomation(projectId, requirementId)).isTrue();
    }

    @Test
    void hasAutomation_returnsFalseWhenNoTcs() {
        when(testCaseRepo.findCoveringTestCases(projectId, requirementId))
                .thenReturn(List.of());

        assertThat(graphService.hasAutomation(projectId, requirementId)).isFalse();
    }

    // -------------------------------------------------------------------------
    // resolveTestGenMode
    // -------------------------------------------------------------------------

    @Test
    void resolveTestGenMode_noExistingTcs_noReusable_returnsCreateAll() {
        when(testCaseRepo.findCoveringTestCases(projectId, requirementId))
                .thenReturn(List.of());
        when(edgeRepo.findByProjectIdAndFromIdAndFromTier(projectId, requirementId, "REQUIREMENT"))
                .thenReturn(List.of());

        assertThat(graphService.resolveTestGenMode(projectId, requirementId))
                .isEqualTo(TestGenMode.CREATE_ALL);
    }

    @Test
    void resolveTestGenMode_tcsNeedsUpdate_returnsUpdateChanged() {
        PlatformTestCase needsUpdate = makeTc(UUID.randomUUID(), false);
        needsUpdate.markNeedsUpdate();
        when(testCaseRepo.findCoveringTestCases(projectId, requirementId))
                .thenReturn(List.of(needsUpdate));

        assertThat(graphService.resolveTestGenMode(projectId, requirementId))
                .isEqualTo(TestGenMode.UPDATE_CHANGED);
    }

    @Test
    void resolveTestGenMode_allTcsActive_returnsNoAction() {
        PlatformTestCase active = makeTc(UUID.randomUUID(), false);
        when(testCaseRepo.findCoveringTestCases(projectId, requirementId))
                .thenReturn(List.of(active));

        assertThat(graphService.resolveTestGenMode(projectId, requirementId))
                .isEqualTo(TestGenMode.NO_ACTION);
    }

    @Test
    void resolveTestGenMode_noTcsButReusableCandidates_returnsReuseFromRelated() {
        UUID relatedReqId = UUID.randomUUID();
        UUID linkedTcId   = UUID.randomUUID();

        when(testCaseRepo.findCoveringTestCases(projectId, requirementId))
                .thenReturn(List.of());

        // linked edge from requirementId → relatedReqId with CLONED_FROM subtype
        PlatformTraceabilityEdge link = new PlatformTraceabilityEdge(
                projectId, requirementId, "REQUIREMENT",
                relatedReqId, "REQUIREMENT", EdgeType.LINKED_TO.name())
                .withSubtype("CLONED_FROM");
        when(edgeRepo.findByProjectIdAndFromIdAndFromTier(projectId, requirementId, "REQUIREMENT"))
                .thenReturn(List.of(link));

        // 3 test cases from the related requirement (threshold for REUSE)
        List<PlatformTestCase> reusableTcs = List.of(
                makeTc(UUID.randomUUID(), true),
                makeTc(UUID.randomUUID(), true),
                makeTc(UUID.randomUUID(), false));
        when(testCaseRepo.findCoveringTestCases(projectId, relatedReqId))
                .thenReturn(reusableTcs);

        assertThat(graphService.resolveTestGenMode(projectId, requirementId))
                .isEqualTo(TestGenMode.REUSE_FROM_RELATED);
    }

    // -------------------------------------------------------------------------
    // buildRequirementContext
    // -------------------------------------------------------------------------

    @Test
    void buildRequirementContext_populatesAllGraphNeighbourhood() {
        PlatformRequirement req = makeRequirement(requirementId, null);
        when(requirementRepo.findById(requirementId)).thenReturn(Optional.of(req));
        when(requirementRepo.findByProjectIdAndParentId(projectId, requirementId))
                .thenReturn(List.of());
        when(edgeRepo.findByProjectIdAndFromIdAndFromTier(projectId, requirementId, "REQUIREMENT"))
                .thenReturn(List.of());
        when(testCaseRepo.findCoveringTestCases(projectId, requirementId))
                .thenReturn(List.of());
        // configId is null in makeRequirement → resolveSource returns GITHUB without querying configRepo

        RequirementContext ctx = graphService.buildRequirementContext(projectId, requirementId);

        assertThat(ctx).isNotNull();
        assertThat(ctx.target()).isNotNull();
        assertThat(ctx.target().id()).isEqualTo(requirementId);
        assertThat(ctx.ancestors()).isEmpty();
        assertThat(ctx.children()).isEmpty();
        assertThat(ctx.links()).isEmpty();
        assertThat(ctx.resolvedTestGenMode()).isEqualTo(TestGenMode.CREATE_ALL);
    }

    @Test
    void buildRequirementContext_requirementNotFound_returnsNull() {
        when(requirementRepo.findById(requirementId)).thenReturn(Optional.empty());

        RequirementContext ctx = graphService.buildRequirementContext(projectId, requirementId);
        assertThat(ctx).isNull();
    }

    // -------------------------------------------------------------------------
    // upsertEdge
    // -------------------------------------------------------------------------

    @Test
    void upsertEdge_existingEdge_doesNotSaveDuplicate() {
        UUID fromId = UUID.randomUUID();
        UUID toId   = UUID.randomUUID();
        PlatformTraceabilityEdge existing = new PlatformTraceabilityEdge(
                projectId, fromId, "TEST_CASE", toId, "REQUIREMENT", "COVERED_BY");
        when(edgeRepo.findByProjectIdAndFromIdAndToIdAndEdgeType(
                projectId, fromId, toId, "COVERED_BY"))
                .thenReturn(Optional.of(existing));

        graphService.upsertEdge(projectId, fromId, "TEST_CASE", toId, "REQUIREMENT",
                EdgeType.COVERED_BY, null);

        verify(edgeRepo, never()).save(any());
    }

    @Test
    void upsertEdge_newEdge_saves() {
        UUID fromId = UUID.randomUUID();
        UUID toId   = UUID.randomUUID();
        when(edgeRepo.findByProjectIdAndFromIdAndToIdAndEdgeType(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(edgeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        graphService.upsertEdge(projectId, fromId, "REQUIREMENT", toId, "REQUIREMENT",
                EdgeType.PARENT_OF, null);

        verify(edgeRepo).save(any(PlatformTraceabilityEdge.class));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private PlatformRequirement makeRequirement(UUID id, UUID parentId) {
        PlatformRequirement req = new PlatformRequirement(
                projectId, null, "EXT-" + id.toString().substring(0, 4),
                "Req title " + id, "Description", "STORY");
        // Set ID via reflection — entity uses GenerationType.UUID in prod
        setField(req, "id", id);
        setField(req, "parentId", parentId);
        return req;
    }

    private PlatformTestCase makeTc(UUID id, boolean hasAutomation) {
        PlatformTestCase tc = new PlatformTestCase(
                projectId, "Test case " + id, List.of(), "AGENT", null);
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
