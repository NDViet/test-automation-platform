package com.platform.agent.api;

import com.platform.core.domain.AgentReviewRequest;
import com.platform.core.domain.AgentWorkflow;
import com.platform.core.domain.ImpactAnalysis;
import com.platform.core.domain.PlatformTestCase;
import com.platform.core.repository.AgentReviewRequestRepository;
import com.platform.core.repository.AgentWorkflowRepository;
import com.platform.core.repository.ImpactAnalysisRepository;
import com.platform.core.repository.PlatformTestCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/**
 * Work-In-Progress hub endpoint — unified feed of items needing human attention per project.
 *
 * GET  /hub/wip/{projectId}                                    — list all WIP items
 * POST /hub/wip/review-requests/{requestId}/approve            — approve an AgentReviewRequest
 * POST /hub/wip/review-requests/{requestId}/reject             — reject an AgentReviewRequest
 */
@RestController
@RequestMapping("/hub/wip")
public class WorkInProgressController {

    private static final Logger log = LoggerFactory.getLogger(WorkInProgressController.class);

    private final PlatformTestCaseRepository   testCaseRepo;
    private final AgentReviewRequestRepository reviewRequestRepo;
    private final AgentWorkflowRepository      workflowRepo;
    private final ImpactAnalysisRepository     impactRepo;

    public WorkInProgressController(PlatformTestCaseRepository   testCaseRepo,
                                    AgentReviewRequestRepository reviewRequestRepo,
                                    AgentWorkflowRepository      workflowRepo,
                                    ImpactAnalysisRepository     impactRepo) {
        this.testCaseRepo      = testCaseRepo;
        this.reviewRequestRepo = reviewRequestRepo;
        this.workflowRepo      = workflowRepo;
        this.impactRepo        = impactRepo;
    }

    // ── GET /hub/wip/{projectId} ──────────────────────────────────────────────

    @GetMapping("/{projectId}")
    public ResponseEntity<List<WipItem>> list(@PathVariable UUID projectId) {
        List<WipItem> items = new ArrayList<>();

        // 1. Test cases under review (limit 50)
        List<PlatformTestCase> underReview = testCaseRepo.findByProjectIdAndStatus(projectId, "UNDER_REVIEW");
        underReview.stream()
                .limit(50)
                .map(tc -> {
                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("testCaseId", tc.getId());
                    meta.put("projectId",  tc.getProjectId());
                    meta.put("suiteId",    tc.getSuiteId());
                    meta.put("createdBy",  tc.getCreatedBy());
                    return new WipItem(
                            tc.getId().toString(),
                            "TEST_CASE_REVIEW",
                            "PENDING",
                            tc.getTitle(),
                            "AI-generated test case awaiting human review",
                            null,
                            tc.getCreatedAt(),
                            meta);
                })
                .forEach(items::add);

        // 2. Test cases with automation PR raised (limit 50)
        List<PlatformTestCase> prRaised = testCaseRepo.findByProjectIdAndAutomationStatus(projectId, "PR_CREATED");
        prRaised.stream()
                .limit(50)
                .map(tc -> {
                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("testCaseId",      tc.getId());
                    meta.put("projectId",       tc.getProjectId());
                    meta.put("automationPrUrl", tc.getAutomationPrUrl());
                    return new WipItem(
                            tc.getId().toString() + "-pr",
                            "AUTOMATION_PR",
                            "PENDING",
                            "Automation PR: " + tc.getTitle(),
                            "Draft PR raised to test automation repo — review and merge",
                            tc.getAutomationPrUrl(),
                            tc.getCreatedAt(),
                            meta);
                })
                .forEach(items::add);

        // 3. Pending agent review requests for this project
        List<AgentReviewRequest> reviewRequests = reviewRequestRepo.findPendingByProjectId(projectId);
        reviewRequests.forEach(rr -> {
            String summary = rr.getSummary();
            String title = "Agent Review: " + (summary != null && !summary.isBlank()
                    ? (summary.length() > 80 ? summary.substring(0, 80) : summary)
                    : "Review required");
            String description = summary != null && !summary.isBlank()
                    ? summary : "Agent is requesting a decision before continuing";

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("reviewRequestId",  rr.getId());
            meta.put("workflowId",       rr.getWorkflowId());
            meta.put("channel",          rr.getChannel());
            meta.put("artifactManifest", rr.getArtifactManifest());
            meta.put("expiresAt",        rr.getExpiresAt() != null ? rr.getExpiresAt().toString() : null);

            items.add(new WipItem(
                    rr.getId().toString(),
                    "AGENT_REVIEW",
                    "PENDING",
                    title,
                    description,
                    null,
                    rr.getCreatedAt(),
                    meta));
        });

        // 4. Active workflows (RUNNING, PENDING, AWAITING_REVIEW) — deduplicated, limit 20
        Map<UUID, AgentWorkflow> workflowMap = new LinkedHashMap<>();
        for (String status : List.of("RUNNING", "PENDING", "AWAITING_REVIEW")) {
            List<AgentWorkflow> batch = workflowRepo.findByProjectIdAndStatusOrderByCreatedAtDesc(projectId, status);
            for (AgentWorkflow w : batch) {
                workflowMap.putIfAbsent(w.getId(), w);
            }
        }
        workflowMap.values().stream()
                .sorted(Comparator.comparing(AgentWorkflow::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(20)
                .forEach(w -> {
                    String triggerSource = w.getTriggerSource();
                    String title = "Workflow: " + w.getTriggerType()
                            + (triggerSource != null ? " via " + triggerSource : "");

                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("workflowId",     w.getId());
                    meta.put("triggerType",    w.getTriggerType());
                    meta.put("triggerSource",  w.getTriggerSource());
                    meta.put("startedAt",      w.getStartedAt() != null ? w.getStartedAt().toString() : null);

                    items.add(new WipItem(
                            w.getId().toString(),
                            "WORKFLOW",
                            w.getStatus(),
                            title,
                            "status: " + w.getStatus(),
                            null,
                            w.getCreatedAt(),
                            meta));
                });

        // 5. Completed impact analyses with suggestions (limit 10)
        impactRepo.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .filter(a -> "COMPLETED".equals(a.getStatus()) && a.getSuggestions() != null)
                .limit(10)
                .forEach(a -> {
                    int suggestionCount = a.getSuggestions().size();
                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("analysisId",      a.getId());
                    meta.put("linkedPrs",       a.getLinkedPrs());
                    meta.put("suggestionCount", suggestionCount);

                    items.add(new WipItem(
                            a.getId().toString(),
                            "IMPACT_ANALYSIS",
                            "COMPLETED",
                            a.getName(),
                            "Impact analysis completed with " + suggestionCount + " suggestion(s) to review",
                            null,
                            a.getCreatedAt(),
                            meta));
                });

        // Sort merged list by createdAt desc (nulls last)
        items.sort(Comparator.comparing(WipItem::createdAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return ResponseEntity.ok(items);
    }

    // ── POST /hub/wip/review-requests/{requestId}/approve ────────────────────

    @PostMapping("/review-requests/{requestId}/approve")
    public ResponseEntity<?> approve(@PathVariable UUID requestId,
                                     @RequestBody DecideRequest body) {
        return reviewRequestRepo.findById(requestId)
                .map(req -> {
                    req.approve(body.decidedBy());
                    return ResponseEntity.ok((Object) reviewRequestRepo.save(req));
                })
                .orElseGet(() -> {
                    log.warn("approve: review request {} not found", requestId);
                    return ResponseEntity.notFound().build();
                });
    }

    // ── POST /hub/wip/review-requests/{requestId}/reject ─────────────────────

    @PostMapping("/review-requests/{requestId}/reject")
    public ResponseEntity<?> reject(@PathVariable UUID requestId,
                                    @RequestBody DecideRequest body) {
        return reviewRequestRepo.findById(requestId)
                .map(req -> {
                    req.reject(body.decidedBy());
                    return ResponseEntity.ok((Object) reviewRequestRepo.save(req));
                })
                .orElseGet(() -> {
                    log.warn("reject: review request {} not found", requestId);
                    return ResponseEntity.notFound().build();
                });
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    public record WipItem(
            String id,
            String itemType,
            String status,
            String title,
            String description,
            String actionUrl,
            Instant createdAt,
            Map<String, Object> metadata) {}

    public record DecideRequest(String decidedBy) {}
}
