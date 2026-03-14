package com.platform.common.kafka;

/**
 * Central registry of all Kafka topic names used across the platform.
 * Import this class in producers and consumers — never hardcode topic strings.
 */
public final class Topics {

    private Topics() {}

    /** Raw normalized test results published by platform-ingestion after each CI run. */
    public static final String TEST_RESULTS_RAW = "test.results.raw";

    /** Enriched results after AI analysis — published by platform-ai. */
    public static final String TEST_RESULTS_ANALYZED = "test.results.analyzed";

    /** Flakiness score updated events — published by platform-analytics. */
    public static final String FLAKINESS_EVENTS = "test.flakiness.events";

    /** Commands to create/update/close issue tracker tickets. */
    public static final String INTEGRATION_COMMANDS = "test.integration.commands";

    /** Alert events dispatched to Slack/email/webhooks. */
    public static final String ALERT_EVENTS = "test.alert.events";
}
