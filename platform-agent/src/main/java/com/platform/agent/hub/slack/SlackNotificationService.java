package com.platform.agent.hub.slack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.core.domain.AgentReviewRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends Block Kit approval request messages to Slack when a review request is created.
 * If bot-token is blank (teams without Slack) the service degrades gracefully and skips.
 */
@Service
public class SlackNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SlackNotificationService.class);
    private static final String CHAT_POST_MESSAGE_URL = "https://slack.com/api/chat.postMessage";
    private static final int SUMMARY_MAX_CHARS = 300;

    @Value("${platform.agent.slack.bot-token:}")
    private String botToken;

    @Value("${platform.agent.slack.approval-channel:#agent-approvals}")
    private String approvalChannel;

    private final RestClient restClient;
    private final ObjectMapper mapper;

    public SlackNotificationService(ObjectMapper mapper) {
        this.mapper     = mapper;
        this.restClient = RestClient.create();
    }

    /**
     * Sends a Block Kit approval message for the given review request.
     * No-ops if bot-token is blank.
     */
    public void sendApprovalRequest(AgentReviewRequest req) {
        if (botToken == null || botToken.isBlank()) {
            log.warn("Slack bot-token is not configured — skipping approval notification for reviewRequest={}", req.getId());
            return;
        }

        try {
            String body = buildBlockKitPayload(req);
            log.debug("Sending Slack approval message for reviewRequest={} channel={}", req.getId(), approvalChannel);

            String response = restClient.post()
                    .uri(CHAT_POST_MESSAGE_URL)
                    .header("Authorization", "Bearer " + botToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            boolean ok = response != null && response.contains("\"ok\":true");
            log.info("Slack approval message sent for reviewRequest={} ok={}", req.getId(), ok);
        } catch (Exception e) {
            log.error("Failed to send Slack approval message for reviewRequest={}", req.getId(), e);
        }
    }

    private String buildBlockKitPayload(AgentReviewRequest req) throws Exception {
        String requestId = req.getId().toString();
        String channel   = req.getChannel() != null ? req.getChannel() : "UNKNOWN";
        String dest      = req.getDestination() != null ? req.getDestination() : "unknown";
        String summary   = truncate(req.getSummary(), SUMMARY_MAX_CHARS);
        String expiresAt = req.getExpiresAt() != null ? req.getExpiresAt().toString() : "N/A";

        List<Map<String, Object>> blocks = new ArrayList<>();

        // Header block
        blocks.add(block("header", textObject("plain_text", "Agent Review Required — " + channel + " / " + dest, true)));

        // Section block — summary
        blocks.add(block("section", textObject("mrkdwn", summary)));

        // Context block — requestId + expiry
        Map<String, Object> contextBlock = new LinkedHashMap<>();
        contextBlock.put("type", "context");
        contextBlock.put("elements", List.of(
                textObject("mrkdwn", "Request ID: `" + requestId + "` • Expires: " + expiresAt)
        ));
        blocks.add(contextBlock);

        // Actions block — 4 buttons
        Map<String, Object> actionsBlock = new LinkedHashMap<>();
        actionsBlock.put("type", "actions");
        actionsBlock.put("elements", List.of(
                button("Approve",    "approve", requestId, null),
                button("Reject",     "reject",  requestId, "danger"),
                button("Edit",       "edit",    requestId, null),
                button("Defer",      "defer",   requestId, null)
        ));
        blocks.add(actionsBlock);

        // Divider block
        blocks.add(Map.of("type", "divider"));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channel", approvalChannel);
        payload.put("blocks", blocks);

        return mapper.writeValueAsString(payload);
    }

    private static Map<String, Object> block(String type, Map<String, Object> textObj) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("type", type);
        b.put("text", textObj);
        return b;
    }

    private static Map<String, Object> textObject(String type, String text) {
        return textObject(type, text, false);
    }

    private static Map<String, Object> textObject(String type, String text, boolean emoji) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("type", type);
        t.put("text", text != null ? text : "");
        if ("plain_text".equals(type)) {
            t.put("emoji", emoji);
        }
        return t;
    }

    private static Map<String, Object> button(String label, String actionId, String value, String style) {
        Map<String, Object> btn = new LinkedHashMap<>();
        btn.put("type", "button");
        btn.put("text", textObject("plain_text", label, true));
        btn.put("action_id", actionId);
        btn.put("value", value);
        if (style != null) {
            btn.put("style", style);
        }
        return btn;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }
}
