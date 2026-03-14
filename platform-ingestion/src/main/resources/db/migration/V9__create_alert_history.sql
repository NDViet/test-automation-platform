CREATE TABLE alert_history (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_name   VARCHAR(200) NOT NULL,
    severity    VARCHAR(20)  NOT NULL,
    message     TEXT         NOT NULL,
    team_id     VARCHAR(200),
    project_id  VARCHAR(200),
    run_id      VARCHAR(200),
    channels    VARCHAR(200),
    delivered   BOOLEAN      NOT NULL DEFAULT false,
    fired_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_ah_project_id ON alert_history(project_id);
CREATE INDEX idx_ah_fired_at   ON alert_history(fired_at);
CREATE INDEX idx_ah_severity   ON alert_history(severity);
