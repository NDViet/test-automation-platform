package com.platform.analytics.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single log event from OpenSearch {@code test_execution_logs-*} index.
 *
 * <p>Field names match those emitted by {@code logstash-logback-encoder} after
 * Logstash normalisation (camelCase MDC keys renamed to snake_case).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LogEntry(

        @JsonProperty("@timestamp")
        String timestamp,

        @JsonProperty("level")
        String level,

        @JsonProperty("message")
        String message,

        /** Suite-level run ID — all tests in one {@code mvn test} invocation share this. */
        @JsonProperty("run_id")
        String runId,

        /** Per-test ID — {@code ClassName#methodName} or {@code feature#scenario}. */
        @JsonProperty("test_id")
        String testId,

        @JsonProperty("team_id")
        String teamId,

        @JsonProperty("project_id")
        String projectId,

        @JsonProperty("trace_id")
        String traceId,

        /** Current BDD/test step name at the time the log was emitted. */
        @JsonProperty("step")
        String step,

        @JsonProperty("test_method")
        String testMethod,

        @JsonProperty("test_class")
        String testClass,

        @JsonProperty("logger")
        String logger,

        @JsonProperty("thread")
        String thread,

        /** Present only for ERROR/WARN lines with an attached exception. */
        @JsonProperty("stack_trace")
        String stackTrace
) {}
