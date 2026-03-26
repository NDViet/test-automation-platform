-- V21: TIA events table — records every Test Impact Analysis query call and its outcome.
--
-- One row per analyse() invocation. Used by Grafana to trend reduction %, risk level
-- distribution, and coverage effectiveness over time per project.

CREATE TABLE tia_events (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    project_id        UUID        NOT NULL,

    -- Query inputs
    changed_classes   INTEGER     NOT NULL DEFAULT 0,  -- distinct class names checked

    -- Query outcome
    total_tests       INTEGER     NOT NULL DEFAULT 0,  -- total mapped tests at query time
    selected_tests    INTEGER     NOT NULL DEFAULT 0,  -- tests recommended
    uncovered_classes INTEGER     NOT NULL DEFAULT 0,  -- changed classes with no mapping
    reduction_pct     DOUBLE PRECISION,                -- 0.0–100.0
    risk_level        VARCHAR(10) NOT NULL,             -- LOW|MEDIUM|HIGH|CRITICAL

    -- Context
    branch            VARCHAR(500),
    triggered_by      VARCHAR(50) NOT NULL DEFAULT 'api',  -- api|ci|portal

    queried_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_tia_events PRIMARY KEY (id),
    CONSTRAINT fk_tia_project FOREIGN KEY (project_id)
                                   REFERENCES projects (id)
                                   ON DELETE CASCADE,
    CONSTRAINT chk_tia_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH','CRITICAL'))
);

CREATE INDEX idx_tia_project_time ON tia_events (project_id, queried_at DESC);
CREATE INDEX idx_tia_risk_time    ON tia_events (risk_level, queried_at DESC);
