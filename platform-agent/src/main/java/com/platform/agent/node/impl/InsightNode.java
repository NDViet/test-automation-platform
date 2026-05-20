package com.platform.agent.node.impl;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.agent.*;
import com.platform.agent.node.AgentNode;
import com.platform.agent.node.AgentOrchestrator;
import com.platform.agent.node.tools.PlatformInsightTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generates nightly digest reports by querying platform-analytics and
 * optionally posting a summary to Slack.
 *
 * Tools: platform_get_trends, platform_get_flakiness_leaderboard,
 *        platform_get_quality_gate, slack_post_message
 */
@Component
public class InsightNode implements AgentNode {

    private static final Logger log = LoggerFactory.getLogger(InsightNode.class);

    private final AgentOrchestrator    orchestrator;
    private final PlatformInsightTools insightTools;
    private final ObjectMapper         mapper;
    private final String               slackBotToken;
    private final RestClient           slackRestClient;

    public InsightNode(AgentOrchestrator orchestrator,
                       PlatformInsightTools insightTools,
                       ObjectMapper mapper,
                       @Value("${platform.agent.slack.bot-token:}") String slackBotToken) {
        this.orchestrator    = orchestrator;
        this.insightTools    = insightTools;
        this.mapper          = mapper;
        this.slackBotToken   = slackBotToken;
        this.slackRestClient = RestClient.builder()
                .baseUrl("https://slack.com")
                .defaultHeader("Content-Type", "application/json; charset=utf-8")
                .build();
    }

    @Override public AgentTaskType taskType() { return AgentTaskType.GENERATE_NIGHTLY_DIGEST; }
    @Override public NodeType      nodeType() { return NodeType.INSIGHT; }

    @Override
    public NodeResult execute(ContextBundle bundle) {
        return orchestrator.run(bundle, this);
    }

    @Override
    public List<Tool> tools() {
        return List.of(
                Tool.builder()
                        .name("platform_get_trends")
                        .description("Retrieve 7-day pass-rate trends for a project from platform-analytics.")
                        .inputSchema(Tool.InputSchema.builder()
                                .type(JsonValue.from("object"))
                                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                                        "project_id", Map.of("type", "string", "description", "Project UUID"),
                                        "days",       Map.of("type", "integer", "default", 7)
                                )))
                                .addRequired("project_id")
                                .build())
                        .build(),

                Tool.builder()
                        .name("platform_get_flakiness_leaderboard")
                        .description("Get the top flaky tests for a project, sorted by flakiness score.")
                        .inputSchema(Tool.InputSchema.builder()
                                .type(JsonValue.from("object"))
                                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                                        "project_id", Map.of("type", "string"),
                                        "limit",      Map.of("type", "integer", "default", 10)
                                )))
                                .addRequired("project_id")
                                .build())
                        .build(),

                Tool.builder()
                        .name("platform_get_quality_gate")
                        .description("Get the current quality gate status (pass/fail) for a project.")
                        .inputSchema(Tool.InputSchema.builder()
                                .type(JsonValue.from("object"))
                                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                                        "project_id", Map.of("type", "string")
                                )))
                                .addRequired("project_id")
                                .build())
                        .build(),

                Tool.builder()
                        .name("slack_post_message")
                        .description("Post the completed digest to a Slack channel. " +
                                "Call this as the final step after composing the digest.")
                        .inputSchema(Tool.InputSchema.builder()
                                .type(JsonValue.from("object"))
                                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                                        "channel", Map.of("type", "string",
                                                "description", "Slack channel ID or name, e.g. #qa-digest"),
                                        "text",    Map.of("type", "string",
                                                "description", "The digest message text (Slack mrkdwn format)")
                                )))
                                .addRequired("channel")
                                .addRequired("text")
                                .build())
                        .build()
        );
    }

    @Override
    public String dispatchToolCall(String toolName, String inputJson, ContextBundle bundle) {
        return switch (toolName) {
            case "platform_get_trends"              -> handleGetTrends(inputJson, bundle);
            case "platform_get_flakiness_leaderboard" -> handleGetFlakiness(inputJson, bundle);
            case "platform_get_quality_gate"        -> handleGetQualityGate(inputJson, bundle);
            case "slack_post_message"               -> handleSlackPost(inputJson, bundle);
            default -> "Unknown tool: " + toolName;
        };
    }

    // -------------------------------------------------------------------------

    private String handleGetTrends(String inputJson, ContextBundle bundle) {
        try {
            JsonNode input = mapper.readTree(inputJson);
            UUID projectId = UUID.fromString(input.path("project_id").asText(bundle.projectId().toString()));
            int days = input.path("days").asInt(7);
            return insightTools.getTrends(projectId, days);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private String handleGetFlakiness(String inputJson, ContextBundle bundle) {
        try {
            JsonNode input = mapper.readTree(inputJson);
            UUID projectId = UUID.fromString(input.path("project_id").asText(bundle.projectId().toString()));
            int limit = input.path("limit").asInt(10);
            return insightTools.getFlakinessLeaderboard(projectId, limit);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private String handleGetQualityGate(String inputJson, ContextBundle bundle) {
        try {
            JsonNode input = mapper.readTree(inputJson);
            UUID projectId = UUID.fromString(input.path("project_id").asText(bundle.projectId().toString()));
            return insightTools.getQualityGate(projectId);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private String handleSlackPost(String inputJson, ContextBundle bundle) {
        if (slackBotToken == null || slackBotToken.isBlank()) {
            log.warn("slack_post_message called but SLACK_BOT_TOKEN is not configured");
            return "Slack not configured — message not sent.";
        }
        try {
            JsonNode input  = mapper.readTree(inputJson);
            String channel  = input.path("channel").asText();
            String text     = input.path("text").asText();

            Map<String, String> body = Map.of("channel", channel, "text", text);
            String bodyJson = mapper.writeValueAsString(body);

            slackRestClient.post()
                    .uri("/api/chat.postMessage")
                    .header("Authorization", "Bearer " + slackBotToken)
                    .body(bodyJson)
                    .retrieve()
                    .toBodilessEntity();

            log.info("InsightNode posted digest to Slack channel {} for project {}",
                    channel, bundle.projectId());
            return "Message posted to " + channel + " successfully.";
        } catch (Exception e) {
            log.error("Slack post failed: {}", e.getMessage());
            return "Slack post failed: " + e.getMessage();
        }
    }
}
