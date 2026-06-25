package com.platform.agent.hub.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.hub.ReviewGateway;
import com.platform.common.agent.ReviewDecision;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Handles Slack Block Kit interactive component callbacks (button clicks on approval messages).
 *
 * <p>Slack sends POST /hub/webhooks/slack/interactions with body: payload=<urlencoded-json>
 *
 * <p>Signature verification uses HMAC-SHA256 over "v0:{timestamp}:{rawBody}" with the signing
 * secret. The header is: X-Slack-Signature: v0=<hex>
 */
@RestController
@RequestMapping("/hub/webhooks/slack")
public class SlackInteractionController {

  private static final Logger log = LoggerFactory.getLogger(SlackInteractionController.class);

  @Value("${platform.agent.slack.signing-secret:}")
  private String signingSecret;

  private final ReviewGateway reviewGateway;
  private final ObjectMapper mapper;

  public SlackInteractionController(ReviewGateway reviewGateway, ObjectMapper mapper) {
    this.reviewGateway = reviewGateway;
    this.mapper = mapper;
  }

  @PostMapping("/interactions")
  public ResponseEntity<Map<String, Object>> interactions(
      @RequestHeader(value = "X-Slack-Signature", defaultValue = "") String slackSig,
      @RequestHeader(value = "X-Slack-Request-Timestamp", defaultValue = "") String timestamp,
      @RequestBody String rawBody) {

    if (!verifySlackSignature(rawBody, timestamp, slackSig)) {
      log.warn("Slack interaction: invalid signature");
      return ResponseEntity.status(401).body(Map.of("error", "invalid signature"));
    }

    try {
      // Slack sends: payload=<urlencoded-json>
      String jsonPayload =
          URLDecoder.decode(
              rawBody.startsWith("payload=") ? rawBody.substring("payload=".length()) : rawBody,
              StandardCharsets.UTF_8);

      JsonNode root = mapper.readTree(jsonPayload);
      JsonNode actions = root.path("actions");
      if (!actions.isArray() || actions.isEmpty()) {
        return ResponseEntity.ok(Map.of("ok", true));
      }

      JsonNode action = actions.get(0);
      String actionId = action.path("action_id").asText("");
      String value = action.path("value").asText("");

      if (actionId.isBlank() || value.isBlank()) {
        log.warn("Slack interaction: missing action_id or value");
        return ResponseEntity.ok(Map.of("ok", true));
      }

      UUID requestId = UUID.fromString(value);
      ReviewDecision decision =
          switch (actionId) {
            case "approve" -> ReviewDecision.APPROVED;
            case "reject" -> ReviewDecision.REJECTED;
            case "edit" -> ReviewDecision.EDIT;
            case "defer" -> ReviewDecision.DEFER;
            default -> {
              log.warn("Slack interaction: unknown action_id '{}'", actionId);
              yield null;
            }
          };

      if (decision == null) return ResponseEntity.ok(Map.of("ok", true));

      String actor = root.path("user").path("username").asText("slack-user");
      log.info("Slack interaction: {} decision='{}' by '{}'", requestId, decision, actor);
      reviewGateway.applyDecision(requestId, decision, null);

      // Return a Slack-compatible acknowledgement (replaces the message buttons with confirmation
      // text)
      return ResponseEntity.ok(
          Map.of("ok", true, "text", "Decision recorded: " + decision.name() + " by @" + actor));

    } catch (IllegalArgumentException e) {
      log.warn("Slack interaction: invalid review request ID — {}", e.getMessage());
      return ResponseEntity.badRequest().body(Map.of("error", "invalid request id"));
    } catch (Exception e) {
      log.error("Slack interaction: processing error", e);
      return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
    }
  }

  private boolean verifySlackSignature(String rawBody, String timestamp, String slackSig) {
    if (signingSecret == null || signingSecret.isBlank()) return true; // not configured
    if (slackSig.isBlank() || timestamp.isBlank()) return false;
    try {
      String baseString = "v0:" + timestamp + ":" + rawBody;
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      String computed =
          "v0="
              + HexFormat.of().formatHex(mac.doFinal(baseString.getBytes(StandardCharsets.UTF_8)));
      if (computed.length() != slackSig.length()) return false;
      int diff = 0;
      for (int i = 0; i < computed.length(); i++) diff |= computed.charAt(i) ^ slackSig.charAt(i);
      return diff == 0;
    } catch (Exception e) {
      log.error("Slack signature verification error", e);
      return false;
    }
  }
}
