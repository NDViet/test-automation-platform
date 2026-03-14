package com.platform.analytics.alerts;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Alert rules and Slack webhook loaded from {@code application.yml}.
 *
 * <pre>
 * platform:
 *   analytics:
 *     alerts:
 *       slack-webhook-url: https://hooks.slack.com/...
 *       rules:
 *         - name: "High Failure Rate"
 *           metric: PASS_RATE_BELOW
 *           threshold: 0.80
 *           severity: HIGH
 *           enabled: true
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "platform.analytics.alerts")
public class AlertProperties {

    private String slackWebhookUrl;

    /**
     * Optional generic webhook URL. Receives a JSON POST with the same payload
     * as the Slack message — useful for custom integrations (PagerDuty, Teams, etc.)
     */
    private String webhookUrl;

    private List<AlertRule> rules = new ArrayList<>();

    public String getSlackWebhookUrl() { return slackWebhookUrl; }
    public void setSlackWebhookUrl(String v) { this.slackWebhookUrl = v; }

    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String v) { this.webhookUrl = v; }

    public List<AlertRule> getRules() { return rules; }
    public void setRules(List<AlertRule> v) { this.rules = v; }
}
