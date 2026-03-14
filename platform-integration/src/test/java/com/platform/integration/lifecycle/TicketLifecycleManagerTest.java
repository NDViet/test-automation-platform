package com.platform.integration.lifecycle;

import com.platform.common.dto.TestCaseResultDto;
import com.platform.common.dto.UnifiedTestResult;
import com.platform.common.enums.SourceFormat;
import com.platform.common.enums.TestStatus;
import com.platform.core.domain.IntegrationConfig;
import com.platform.core.domain.IssueTrackerLink;
import com.platform.core.domain.TestCaseResult;
import com.platform.core.domain.TestExecution;
import com.platform.core.repository.FlakinessScoreRepository;
import com.platform.core.repository.TestCaseResultRepository;
import com.platform.integration.port.IssueReference;
import com.platform.integration.port.IssueTrackerPort;
import com.platform.integration.port.IssueRequest;
import com.platform.integration.port.IssueUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketLifecycleManagerTest {

    @Mock TestCaseResultRepository resultRepo;
    @Mock FlakinessScoreRepository scoreRepo;
    @Mock DuplicateDetector duplicateDetector;

    IssueDecisionEngine decisionEngine;
    TicketLifecycleManager manager;

    UUID projectId = UUID.randomUUID();
    UUID teamId    = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        decisionEngine = new IssueDecisionEngine();
        manager = new TicketLifecycleManager(resultRepo, scoreRepo, decisionEngine, duplicateDetector);
    }

    @Test
    void createsTicketAfter3ConsecutiveFailures() {
        // 3 existing failures in DB history
        List<TestCaseResult> history = nFailures(3);
        when(resultRepo.findWithExecutionByTestIdAndProjectIdSince(any(), any(), any()))
                .thenReturn(history);
        when(scoreRepo.findByTestIdAndProjectId(any(), any())).thenReturn(Optional.empty());
        when(duplicateDetector.findLinkedTestIds(any(), any())).thenReturn(List.of());
        when(duplicateDetector.findExisting(any(), any(), any())).thenReturn(Optional.empty());

        IssueTrackerPort tracker = mockTracker("JIRA", "PROJ-1");
        IntegrationConfig config = config(teamId, "JIRA", "PROJ");

        UnifiedTestResult result = failingResult("com.example.Test#login");
        manager.processRun(result, projectId, config, tracker);

        ArgumentCaptor<IssueRequest> captor = ArgumentCaptor.forClass(IssueRequest.class);
        verify(tracker).createIssue(captor.capture());
        assertThat(captor.getValue().issueType()).isEqualTo("Bug");
        assertThat(captor.getValue().testId()).isEqualTo("com.example.Test#login");
    }

    @Test
    void updatesExistingOpenTicketOngoingFailure() {
        List<TestCaseResult> history = nFailures(2);
        when(resultRepo.findWithExecutionByTestIdAndProjectIdSince(any(), any(), any()))
                .thenReturn(history);
        when(scoreRepo.findByTestIdAndProjectId(any(), any())).thenReturn(Optional.empty());

        IssueTrackerLink link = new IssueTrackerLink("com.example.Test#login", projectId,
                "JIRA", "PROJ-1", "https://jira/PROJ-1", "Bug");
        link.syncStatus("In Progress");

        when(duplicateDetector.findLinkedTestIds(any(), any()))
                .thenReturn(List.of("com.example.Test#login"));
        when(duplicateDetector.findExisting(any(), any(), any())).thenReturn(Optional.of(link));

        IssueTrackerPort tracker = mockTracker("JIRA", null);
        manager.processRun(failingResult("com.example.Test#login"), projectId,
                config(teamId, "JIRA", "PROJ"), tracker);

        verify(tracker).updateIssue(eq("PROJ-1"), any(IssueUpdate.class));
        verify(tracker, never()).createIssue(any());
    }

    @Test
    void closesTicketWhenTestPassesConsecutively() {
        List<TestCaseResult> history = nPasses(3);
        when(resultRepo.findWithExecutionByTestIdAndProjectIdSince(any(), any(), any()))
                .thenReturn(history);
        when(scoreRepo.findByTestIdAndProjectId(any(), any())).thenReturn(Optional.empty());

        IssueTrackerLink link = new IssueTrackerLink("com.example.Test#login", projectId,
                "JIRA", "PROJ-1", "https://jira/PROJ-1", "Bug");
        link.syncStatus("Open");

        when(duplicateDetector.findLinkedTestIds(any(), any()))
                .thenReturn(List.of("com.example.Test#login"));
        when(duplicateDetector.findExisting(any(), any(), any())).thenReturn(Optional.of(link));

        IssueTrackerPort tracker = mockTracker("JIRA", null);
        manager.processRun(passingResult("com.example.Test#login"), projectId,
                config(teamId, "JIRA", "PROJ"), tracker);

        verify(tracker).closeIssue(eq("PROJ-1"), anyString());
        verify(tracker, never()).createIssue(any());
    }

    @Test
    void skipsProcessingWhenNoFailuresAndNoLinks() {
        when(duplicateDetector.findLinkedTestIds(any(), any())).thenReturn(List.of());

        IssueTrackerPort tracker = mockTracker("JIRA", null);
        manager.processRun(passingResult("com.example.Test#ok"), projectId,
                config(teamId, "JIRA", "PROJ"), tracker);

        // No ticket operations should occur
        verify(tracker, never()).createIssue(any());
        verify(tracker, never()).updateIssue(any(), any());
        verify(tracker, never()).closeIssue(any(), any());
        verify(tracker, never()).reopenIssue(any(), any());
        verify(resultRepo, never()).findWithExecutionByTestIdAndProjectIdSince(any(), any(), any());
    }

    @Test
    void highPriorityOnMainBranch() {
        List<TestCaseResult> history = nFailures(3);
        when(resultRepo.findWithExecutionByTestIdAndProjectIdSince(any(), any(), any()))
                .thenReturn(history);
        when(scoreRepo.findByTestIdAndProjectId(any(), any())).thenReturn(Optional.empty());
        when(duplicateDetector.findLinkedTestIds(any(), any())).thenReturn(List.of());
        when(duplicateDetector.findExisting(any(), any(), any())).thenReturn(Optional.empty());

        IssueTrackerPort tracker = mockTracker("JIRA", "PROJ-1");
        manager.processRun(failingResultOnBranch("com.example.Test#m", "main"), projectId,
                config(teamId, "JIRA", "PROJ"), tracker);

        ArgumentCaptor<IssueRequest> captor = ArgumentCaptor.forClass(IssueRequest.class);
        verify(tracker).createIssue(captor.capture());
        assertThat(captor.getValue().priority()).isEqualTo("High");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<TestCaseResult> nFailures(int n) {
        return buildHistory(TestStatus.FAILED, n);
    }

    private List<TestCaseResult> nPasses(int n) {
        return buildHistory(TestStatus.PASSED, n);
    }

    private List<TestCaseResult> buildHistory(TestStatus status, int n) {
        return java.util.stream.IntStream.range(0, n).mapToObj(i ->
                TestCaseResult.builder()
                        .execution(new TestExecution.Builder()
                                .runId(UUID.randomUUID().toString()).environment("staging")
                                .totalTests(1).passed(status == TestStatus.PASSED ? 1 : 0)
                                .failed(status == TestStatus.FAILED ? 1 : 0)
                                .broken(0).skipped(0)
                                .executedAt(Instant.now().minus(i + 1, ChronoUnit.HOURS))
                                .build())
                        .testId("com.example.Test#login")
                        .status(status)
                        .build()
        ).toList();
    }

    private UnifiedTestResult failingResult(String testId) {
        return failingResultOnBranch(testId, "feature/test");
    }

    private UnifiedTestResult failingResultOnBranch(String testId, String branch) {
        TestCaseResultDto tc = TestCaseResultDto.basic(testId, "login", "Test", "login",
                List.of(), TestStatus.FAILED, 100L, "AssertionError", null, 0, List.of());
        return new UnifiedTestResult("run-1", "team-a", "proj-x", branch, "staging",
                null, null, null, null, Instant.now(),
                1, 0, 1, 0, 0, 100L, SourceFormat.JUNIT_XML, List.of(tc),
                "UNKNOWN", 0, "");
    }

    private UnifiedTestResult passingResult(String testId) {
        TestCaseResultDto tc = TestCaseResultDto.basic(testId, "login", "Test", "login",
                List.of(), TestStatus.PASSED, 100L, null, null, 0, List.of());
        return new UnifiedTestResult("run-2", "team-a", "proj-x", "main", "staging",
                null, null, null, null, Instant.now(),
                1, 1, 0, 0, 0, 100L, SourceFormat.JUNIT_XML, List.of(tc),
                "UNKNOWN", 0, "");
    }

    private IntegrationConfig config(UUID teamId, String trackerType, String projectKey) {
        return new IntegrationConfig(teamId, trackerType,
                "https://jira.example.com", projectKey,
                Map.of("email", "user@example.com", "apiToken", "token123"));
    }

    private IssueTrackerPort mockTracker(String type, String newIssueKey) {
        IssueTrackerPort tracker = mock(IssueTrackerPort.class);
        when(tracker.trackerType()).thenReturn(type);
        if (newIssueKey != null) {
            when(tracker.createIssue(any())).thenReturn(
                    new IssueReference(newIssueKey, "https://jira/browse/" + newIssueKey,
                            "Open", "Bug"));
        }
        return tracker;
    }
}
