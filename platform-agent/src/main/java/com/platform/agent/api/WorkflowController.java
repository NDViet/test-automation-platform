package com.platform.agent.api;

import com.platform.common.agent.*;
import com.platform.agent.hub.ContextAssembler;
import com.platform.agent.workflow.AgentWorkflowService;
import com.platform.core.domain.AgentWorkflow;
import com.platform.core.domain.AgentWorkflowStep;
import com.platform.core.repository.AgentWorkflowRepository;
import com.platform.core.repository.AgentWorkflowStepRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/hub/workflows")
public class WorkflowController {

    private final AgentWorkflowService workflowService;
    private final AgentWorkflowRepository workflowRepo;
    private final AgentWorkflowStepRepository stepRepo;
    private final ContextAssembler contextAssembler;

    public WorkflowController(AgentWorkflowService workflowService,
                               AgentWorkflowRepository workflowRepo,
                               AgentWorkflowStepRepository stepRepo,
                               ContextAssembler contextAssembler) {
        this.workflowService    = workflowService;
        this.workflowRepo       = workflowRepo;
        this.stepRepo           = stepRepo;
        this.contextAssembler   = contextAssembler;
    }

    @PostMapping
    public ResponseEntity<UUID> trigger(@RequestBody TriggerWorkflowRequest request) {
        TriggerRef trigger = new TriggerRef(
                TriggerRef.TriggerType.valueOf(request.triggerType()),
                null,
                request.entityType(),
                request.entityExternalId(),
                request.refUrl(),
                request.actorLogin(),
                java.time.Instant.now());

        AgentWorkflow workflow = workflowService.createWorkflow(request.projectId(), trigger);

        // Assemble context and kick off async execution
        ContextBundle bundle = contextAssembler.assemble(workflow.getId(), request.projectId(), trigger);
        workflowService.executeWorkflow(workflow.getId(), bundle);

        return ResponseEntity.created(URI.create("/hub/workflows/" + workflow.getId()))
                .body(workflow.getId());
    }

    @GetMapping("/{workflowId}")
    public ResponseEntity<AgentWorkflow> get(@PathVariable UUID workflowId) {
        return workflowRepo.findById(workflowId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<AgentWorkflow>> listByProject(@RequestParam UUID projectId) {
        return ResponseEntity.ok(workflowRepo.findByProjectIdOrderByCreatedAtDesc(projectId));
    }

    /** PR analyses: GitHub-triggered ANALYZE_PR_DIFF workflows with their AnalysisNode step summaries. */
    @GetMapping("/pr-analyses")
    public ResponseEntity<List<PrAnalysisDto>> prAnalyses(
            @RequestParam UUID projectId,
            @RequestParam(defaultValue = "30") int limit) {

        List<PrAnalysisDto> results = workflowRepo
                .findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .filter(w -> "GITHUB".equals(w.getTriggerSource()))
                .limit(limit)
                .map(w -> {
                    String refUrl  = w.getTriggerRef() != null
                            ? String.valueOf(w.getTriggerRef().getOrDefault("refUrl", ""))
                            : null;
                    String summary = stepRepo
                            .findByWorkflowIdOrderBySequenceOrder(w.getId())
                            .stream()
                            .filter(s -> "ANALYZE_PR_DIFF".equals(s.getTaskType()))
                            .map(AgentWorkflowStep::getSummary)
                            .filter(s -> s != null && !s.isBlank())
                            .findFirst()
                            .orElse(null);
                    return new PrAnalysisDto(
                            w.getId(), w.getProjectId(),
                            refUrl, w.getStatus(), summary,
                            w.getTotalInputTokens(), w.getTotalOutputTokens(),
                            w.getStartedAt(), w.getCompletedAt(), w.getCreatedAt());
                })
                .toList();

        return ResponseEntity.ok(results);
    }

    public record PrAnalysisDto(
            java.util.UUID workflowId,
            java.util.UUID projectId,
            String refUrl,
            String status,
            String summary,
            int totalInputTokens,
            int totalOutputTokens,
            java.time.Instant startedAt,
            java.time.Instant completedAt,
            java.time.Instant createdAt
    ) {}

    public record TriggerWorkflowRequest(
            UUID projectId,
            String triggerType,
            String entityType,
            String entityExternalId,
            String refUrl,
            String actorLogin
    ) {}
}
