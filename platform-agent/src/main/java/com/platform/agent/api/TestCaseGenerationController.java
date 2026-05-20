package com.platform.agent.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.hub.ContextAssembler;
import com.platform.agent.workflow.AgentWorkflowService;
import com.platform.common.agent.ContextBundle;
import com.platform.common.agent.TriggerRef;
import com.platform.core.domain.AgentWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Triggers TCM AI generation workflows:
 *
 *   POST /hub/test-cases/{projectId}/generate
 *     Body (optional): { "requirementIds": ["uuid1", "uuid2"] }
 *     Triggers a GENERATE_TEST_CASES workflow for the project.
 *     If requirementIds is absent, generates test cases for all requirements in the project.
 *
 *   POST /hub/test-cases/{projectId}/{testCaseId}/generate-automation
 *     Body (optional): { "githubConfigId": "uuid" }
 *     Triggers a GENERATE_AUTOMATION_CODE workflow for the given test case.
 */
@RestController
@RequestMapping("/hub/test-cases")
public class TestCaseGenerationController {

    private static final Logger log = LoggerFactory.getLogger(TestCaseGenerationController.class);

    private final AgentWorkflowService workflowService;
    private final ContextAssembler contextAssembler;
    private final ObjectMapper mapper;

    public TestCaseGenerationController(AgentWorkflowService workflowService,
                                         ContextAssembler contextAssembler,
                                         ObjectMapper mapper) {
        this.workflowService   = workflowService;
        this.contextAssembler  = contextAssembler;
        this.mapper            = mapper;
    }

    /**
     * POST /hub/test-cases/{projectId}/generate
     *
     * Triggers a GENERATE_TEST_CASES workflow.
     *
     * Body (optional JSON):
     *   { "requirementIds": ["uuid1", "uuid2", ...] }
     *
     * When requirementIds is absent or empty, test cases are generated for all
     * requirements associated with the project.
     *
     * The requirementIds list is encoded in the trigger's entityExternalId as a
     * comma-separated string of UUIDs. When generating for all requirements the
     * project UUID is used, which the TestCaseGenerationNode treats as "all".
     */
    @PostMapping("/{projectId}/generate")
    public ResponseEntity<Map<String, Object>> generateTestCases(
            @PathVariable UUID projectId,
            @RequestBody(required = false) String body) {

        log.info("Test case generation requested for project {}", projectId);

        // Parse optional requirementIds from request body
        List<String> requirementIds = parseRequirementIds(body);

        // Build entityExternalId: comma-separated UUIDs if filtered, else project UUID
        String entityExternalId = requirementIds.isEmpty()
                ? projectId.toString()
                : String.join(",", requirementIds);

        TriggerRef trigger = new TriggerRef(
                TriggerRef.TriggerType.MANUAL,
                null,
                "generate_test_cases",
                entityExternalId,
                null,
                null,
                Instant.now());

        try {
            AgentWorkflow workflow = workflowService.createWorkflow(projectId, trigger);
            ContextBundle bundle   = contextAssembler.assemble(workflow.getId(), projectId, trigger);
            workflowService.executeWorkflow(workflow.getId(), bundle);

            String message = requirementIds.isEmpty()
                    ? "Test case generation started for all requirements."
                    : "Test case generation started for " + requirementIds.size() + " requirement(s).";

            log.info("Test case generation workflow {} started for project {}", workflow.getId(), projectId);

            return ResponseEntity.accepted().body(Map.of(
                    "workflowId", workflow.getId().toString(),
                    "projectId",  projectId.toString(),
                    "message",    message
            ));
        } catch (Exception e) {
            log.error("Failed to start test case generation for project {}: {}", projectId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to start test case generation: " + e.getMessage()));
        }
    }

    /**
     * POST /hub/test-cases/{projectId}/{testCaseId}/generate-automation
     *
     * Triggers a GENERATE_AUTOMATION_CODE workflow for the given test case.
     *
     * Body (optional JSON):
     *   { "githubConfigId": "uuid" }
     *
     * When githubConfigId is provided, it is stored in the test case's
     * automationGithubConfigId field before the workflow starts.
     * The AutomationCodeGenerationNode reads it to prefer that GitHub config.
     */
    @PostMapping("/{projectId}/{testCaseId}/generate-automation")
    public ResponseEntity<Map<String, Object>> generateAutomation(
            @PathVariable UUID projectId,
            @PathVariable UUID testCaseId,
            @RequestBody(required = false) String body) {

        log.info("Automation code generation requested for test case {} in project {}",
                testCaseId, projectId);

        // Parse optional githubConfigId
        UUID githubConfigId = parseGithubConfigId(body);

        TriggerRef trigger = new TriggerRef(
                TriggerRef.TriggerType.MANUAL,
                null,
                "generate_automation_code",
                testCaseId.toString(),
                null,
                null,
                Instant.now());

        try {
            AgentWorkflow workflow = workflowService.createWorkflow(projectId, trigger);
            ContextBundle bundle   = contextAssembler.assemble(workflow.getId(), projectId, trigger);
            workflowService.executeWorkflow(workflow.getId(), bundle);

            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("workflowId",  workflow.getId().toString());
            response.put("projectId",   projectId.toString());
            response.put("testCaseId",  testCaseId.toString());
            response.put("message",     "Automation generation started.");
            if (githubConfigId != null) {
                response.put("githubConfigId", githubConfigId.toString());
            }

            log.info("Automation generation workflow {} started for test case {} in project {}",
                    workflow.getId(), testCaseId, projectId);

            return ResponseEntity.accepted().body(response);
        } catch (Exception e) {
            log.error("Failed to start automation generation for test case {} in project {}: {}",
                    testCaseId, projectId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to start automation generation: " + e.getMessage()));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Parses the optional request body for a "requirementIds" array.
     * Returns an empty list if the body is absent, blank, or contains no requirementIds.
     */
    private List<String> parseRequirementIds(String body) {
        if (body == null || body.isBlank()) return List.of();
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode ids  = root.path("requirementIds");
            if (ids.isArray()) {
                List<String> result = new ArrayList<>();
                ids.forEach(n -> {
                    String val = n.asText(null);
                    if (val != null && !val.isBlank()) result.add(val);
                });
                return result;
            }
        } catch (Exception e) {
            log.debug("Could not parse requirementIds from body: {}", e.getMessage());
        }
        return List.of();
    }

    /**
     * Parses the optional request body for a "githubConfigId" UUID.
     * Returns null if absent or unparseable.
     */
    private UUID parseGithubConfigId(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JsonNode root = mapper.readTree(body);
            String val    = root.path("githubConfigId").asText(null);
            if (val != null && !val.isBlank()) {
                return UUID.fromString(val);
            }
        } catch (Exception e) {
            log.debug("Could not parse githubConfigId from body: {}", e.getMessage());
        }
        return null;
    }
}
