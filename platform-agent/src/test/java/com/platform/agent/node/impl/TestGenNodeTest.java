package com.platform.agent.node.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.platform.agent.contract.AgentGridFixtures;
import com.platform.agent.node.AgentOrchestrator;
import com.platform.common.agent.ContextBundle;
import com.platform.core.domain.PlatformTestCase;
import com.platform.core.domain.PlatformTraceabilityEdge;
import com.platform.core.repository.PlatformTestCaseRepository;
import com.platform.core.repository.PlatformTraceabilityEdgeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TestGenNode tool dispatch — no Claude API, no DB.
 * Verifies the Hub→Node tool contract: correct entities created, correct sentinels returned.
 */
@ExtendWith(MockitoExtension.class)
class TestGenNodeTest {

    @Mock private AgentOrchestrator orchestrator;
    @Mock private PlatformTestCaseRepository testCaseRepo;
    @Mock private PlatformTraceabilityEdgeRepository edgeRepo;

    private TestGenNode node;
    private ObjectMapper mapper;
    private ContextBundle bundle;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        node   = new TestGenNode(orchestrator, testCaseRepo, edgeRepo, mapper);
        bundle = AgentGridFixtures.bundle();
    }

    // -------------------------------------------------------------------------
    // save_test_cases
    // -------------------------------------------------------------------------

    @Test
    void saveTestCases_createsTcAndCoveredByEdge() throws Exception {
        UUID requirementId = AgentGridFixtures.REQUIREMENT_ID;
        String inputJson = mapper.writeValueAsString(java.util.Map.of(
                "requirement_id", requirementId.toString(),
                "test_cases", List.of(
                        java.util.Map.of("title", "Login with valid credentials",
                                "ac_refs", List.of("AC-1", "AC-2"),
                                "has_automation", false),
                        java.util.Map.of("title", "Login with invalid password",
                                "ac_refs", List.of("AC-3"),
                                "has_automation", true)
                )
        ));

        PlatformTestCase savedTc = new PlatformTestCase(
                AgentGridFixtures.PROJECT_ID, "Login with valid credentials",
                List.of("AC-1", "AC-2"), "AGENT", AgentGridFixtures.SESSION_ID);
        when(testCaseRepo.save(any(PlatformTestCase.class))).thenReturn(savedTc);

        String result = node.dispatchToolCall("save_test_cases", inputJson, bundle);

        assertThat(result).contains("Saved 2 test cases");
        verify(testCaseRepo, times(2)).save(any(PlatformTestCase.class));
        ArgumentCaptor<PlatformTraceabilityEdge> edgeCaptor =
                ArgumentCaptor.forClass(PlatformTraceabilityEdge.class);
        verify(edgeRepo, times(2)).save(edgeCaptor.capture());
        List<PlatformTraceabilityEdge> savedEdges = edgeCaptor.getAllValues();
        assertThat(savedEdges).allMatch(e -> "COVERED_BY".equals(e.getEdgeType()));
        assertThat(savedEdges).allMatch(e -> "TEST_CASE".equals(e.getFromTier()));
        assertThat(savedEdges).allMatch(e -> requirementId.equals(e.getToId()));
    }

    @Test
    void saveTestCases_missingTestCasesArray_returnsError() {
        String inputJson = "{\"requirement_id\": \"" + AgentGridFixtures.REQUIREMENT_ID + "\"}";
        String result = node.dispatchToolCall("save_test_cases", inputJson, bundle);
        assertThat(result).startsWith("Error:");
        verifyNoInteractions(testCaseRepo);
    }

    @Test
    void saveTestCases_invalidUuid_returnsError() {
        String inputJson = "{\"requirement_id\": \"not-a-uuid\", \"test_cases\": []}";
        String result = node.dispatchToolCall("save_test_cases", inputJson, bundle);
        assertThat(result).startsWith("Error");
    }

    // -------------------------------------------------------------------------
    // update_test_case
    // -------------------------------------------------------------------------

    @Test
    void updateTestCase_updatesTitle_acRefs_andMarksActive() throws Exception {
        UUID tcId = AgentGridFixtures.TEST_CASE_ID;
        PlatformTestCase existingTc = new PlatformTestCase(
                AgentGridFixtures.PROJECT_ID, "Old title",
                List.of("AC-1"), "AGENT", null);
        existingTc.markNeedsUpdate();
        when(testCaseRepo.findById(tcId)).thenReturn(Optional.of(existingTc));
        when(testCaseRepo.save(any())).thenReturn(existingTc);

        String inputJson = mapper.writeValueAsString(java.util.Map.of(
                "test_case_id", tcId.toString(),
                "new_title",   "Updated title",
                "new_ac_refs", List.of("AC-1", "AC-2-updated")
        ));
        String result = node.dispatchToolCall("update_test_case", inputJson, bundle);

        assertThat(result).contains("Updated test case").contains("ACTIVE");
        assertThat(existingTc.getTitle()).isEqualTo("Updated title");
        assertThat(existingTc.getAcRefs()).contains("AC-2-updated");
        assertThat(existingTc.getCoverageStatus()).isEqualTo("ACTIVE");
        verify(testCaseRepo).save(existingTc);
    }

    @Test
    void updateTestCase_notFound_returnsMessage() throws Exception {
        UUID tcId = UUID.randomUUID();
        when(testCaseRepo.findById(tcId)).thenReturn(Optional.empty());

        String inputJson = mapper.writeValueAsString(java.util.Map.of(
                "test_case_id", tcId.toString(),
                "new_title", "Some title"
        ));
        String result = node.dispatchToolCall("update_test_case", inputJson, bundle);
        assertThat(result).contains("not found");
        verify(testCaseRepo, never()).save(any());
    }

    @Test
    void updateTestCase_noChanges_returnsNoChangesMessage() throws Exception {
        UUID tcId = AgentGridFixtures.TEST_CASE_ID;
        PlatformTestCase existingTc = new PlatformTestCase(
                AgentGridFixtures.PROJECT_ID, "Title",
                List.of("AC-1"), "AGENT", null);
        when(testCaseRepo.findById(tcId)).thenReturn(Optional.of(existingTc));

        // send empty strings — no changes
        String inputJson = mapper.writeValueAsString(java.util.Map.of(
                "test_case_id", tcId.toString()
        ));
        String result = node.dispatchToolCall("update_test_case", inputJson, bundle);
        assertThat(result).contains("No changes");
        verify(testCaseRepo, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // link_test_cases
    // -------------------------------------------------------------------------

    @Test
    void linkTestCases_createsEdgesForNewLinks() throws Exception {
        UUID reqId = AgentGridFixtures.REQUIREMENT_ID;
        UUID tcId1 = UUID.randomUUID();
        UUID tcId2 = UUID.randomUUID();
        when(edgeRepo.findByProjectIdAndFromIdAndToIdAndEdgeType(any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        String inputJson = mapper.writeValueAsString(java.util.Map.of(
                "requirement_id", reqId.toString(),
                "test_case_ids", List.of(tcId1.toString(), tcId2.toString())
        ));
        String result = node.dispatchToolCall("link_test_cases", inputJson, bundle);

        assertThat(result).contains("Linked 2 existing test cases");
        verify(edgeRepo, times(2)).save(any(PlatformTraceabilityEdge.class));
    }

    @Test
    void linkTestCases_skipsDuplicateEdge() throws Exception {
        UUID reqId = AgentGridFixtures.REQUIREMENT_ID;
        UUID tcId  = UUID.randomUUID();
        PlatformTraceabilityEdge existing = new PlatformTraceabilityEdge(
                AgentGridFixtures.PROJECT_ID, tcId, "TEST_CASE",
                reqId, "REQUIREMENT", "COVERED_BY");
        when(edgeRepo.findByProjectIdAndFromIdAndToIdAndEdgeType(any(), eq(tcId), eq(reqId), eq("COVERED_BY")))
                .thenReturn(Optional.of(existing));

        String inputJson = mapper.writeValueAsString(java.util.Map.of(
                "requirement_id", reqId.toString(),
                "test_case_ids", List.of(tcId.toString())
        ));
        String result = node.dispatchToolCall("link_test_cases", inputJson, bundle);

        assertThat(result).contains("Linked 0 existing test cases");
        verify(edgeRepo, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // request_review (HITL sentinel)
    // -------------------------------------------------------------------------

    @Test
    void requestReview_returnsSentinelPrefix() throws Exception {
        String payload = "{\"test_cases\": [{\"title\": \"Proposed TC\"}]}";
        String inputJson = mapper.writeValueAsString(java.util.Map.of(
                "summary", "Review these 1 proposed test cases",
                "payload", payload
        ));
        String result = node.dispatchToolCall("request_review", inputJson, bundle);

        assertThat(result).startsWith("__AWAITING_REVIEW__");
        assertThat(result).contains(payload);
    }

    // -------------------------------------------------------------------------
    // unknown tool
    // -------------------------------------------------------------------------

    @Test
    void unknownTool_returnsErrorMessage() {
        String result = node.dispatchToolCall("nonexistent_tool", "{}", bundle);
        assertThat(result).contains("Unknown tool");
        verifyNoInteractions(testCaseRepo, edgeRepo);
    }

    // -------------------------------------------------------------------------
    // tools() contract
    // -------------------------------------------------------------------------

    @Test
    void tools_declaresAllExpectedTools() {
        List<String> toolNames = node.tools().stream()
                .map(t -> t.name())
                .toList();
        assertThat(toolNames).containsExactlyInAnyOrder(
                "save_test_cases", "update_test_case", "link_test_cases", "request_review");
    }

    @Test
    void nodeType_andTaskType_areCorrect() {
        assertThat(node.nodeType().name()).isEqualTo("TEST_GEN");
        assertThat(node.taskType().name()).isEqualTo("GENERATE_AUTOMATED_TESTS");
    }
}
