package com.platform.analytics.alerts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Dispatches alert notifications. Currently supports Slack incoming webhooks.
 * All failures are logged at WARN and swallowed — alerts must never affect the main flow.
 */
@Service
public class AlertNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AlertNotificationService.class);

    private final AlertProperties properties;
    private final HttpClient httpClient;

    public AlertNotificationService(AlertProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Sends the alert to all configured channels. Never throws.
     * Returns a comma-separated string of channels that were attempted.
     */
    public String send(AlertEvent event) {
        log.warn("[Alert] severity={} rule='{}' — {}", event.severity(), event.ruleName(), event.message());

        java.util.List<String> attempted = new java.util.ArrayList<>();

        String slackUrl = properties.getSlackWebhookUrl();
        if (slackUrl != null && !slackUrl.isBlank()) {
            postSlack(event, slackUrl);
            attempted.add("SLACK");
        }

        String genericUrl = properties.getWebhookUrl();
        if (genericUrl != null && !genericUrl.isBlank()) {
            postWebhook(event, genericUrl);
            attempted.add("WEBHOOK");
        }

        if (attempted.isEmpty()) {
            log.debug("[Alert] No notification channels configured — alert logged only");
        }
        return String.join(",", attempted);
    }

    private void postSlack(AlertEvent event, String webhookUrl) {
        try {
            String emoji = switch (event.severity()) {
                case CRITICAL -> ":red_circle:";
                case HIGH     -> ":large_orange_circle:";
                case MEDIUM   -> ":large_yellow_circle:";
                case LOW      -> ":white_circle:";
            };
            String body = "{\"text\": \"%s *[%s]* %s\\nProject: `%s` | Team: `%s` | Run: `%s`\"}"
                    .formatted(emoji, event.ruleName(), escapeJson(event.message()),
                            event.projectId(), event.teamId(), event.runId());
            post(webhookUrl, body);
        } catch (Exception e) {
            log.warn("[Alert] Failed to send Slack notification (non-fatal): {}", e.getMessage());
        }
    }

    private void postWebhook(AlertEvent event, String webhookUrl) {
        try {
            String body = ("{\"id\":\"%s\",\"ruleName\":\"%s\",\"severity\":\"%s\"," +
                    "\"message\":\"%s\",\"teamId\":\"%s\",\"projectId\":\"%s\"," +
                    "\"runId\":\"%s\",\"firedAt\":\"%s\"}")
                    .formatted(event.id(), escapeJson(event.ruleName()), event.severity(),
                            escapeJson(event.message()), event.teamId(),
                            event.projectId(), event.runId(), event.firedAt());
            post(webhookUrl, body);
        } catch (Exception e) {
            log.warn("[Alert] Failed to send generic webhook notification (non-fatal): {}", e.getMessage());
        }
    }

    private void post(String url, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("[Alert] Webhook {} returned HTTP {}", url, response.statusCode());
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
