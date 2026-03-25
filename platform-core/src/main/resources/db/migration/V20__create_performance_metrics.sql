-- V20: Performance metrics table — stores response-time percentiles, throughput, error rate,
-- and VU counts for each PERFORMANCE test execution (K6, Gatling, JMeter).
--
-- One row per execution. All time values in milliseconds. Nullable = metric not available
-- in the source report (e.g. JMeter JTL does not include p90/p95 without extra config).

CREATE TABLE performance_metrics (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    execution_id    UUID        NOT NULL,

    -- Response time distribution (ms)
    avg_ms          DOUBLE PRECISION,
    min_ms          DOUBLE PRECISION,
    median_ms       DOUBLE PRECISION,
    max_ms          DOUBLE PRECISION,
    p90_ms          DOUBLE PRECISION,
    p95_ms          DOUBLE PRECISION,
    p99_ms          DOUBLE PRECISION,

    -- Throughput
    requests_total  BIGINT,
    requests_per_second DOUBLE PRECISION,

    -- Reliability (0.0 = no errors, 1.0 = all failed)
    error_rate      DOUBLE PRECISION,

    -- Concurrency
    vus_max         INTEGER,

    -- Scenario wall-clock duration (ms) — may differ from sum of test-case durations
    duration_ms     BIGINT,

    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT pk_performance_metrics  PRIMARY KEY (id),
    CONSTRAINT fk_pm_execution         FOREIGN KEY (execution_id)
                                           REFERENCES test_executions (id)
                                           ON DELETE CASCADE,
    CONSTRAINT uq_pm_execution         UNIQUE (execution_id)
);

CREATE INDEX idx_pm_execution_id ON performance_metrics (execution_id);
