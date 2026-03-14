CREATE TABLE audit_events (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type        VARCHAR(50)  NOT NULL,
    actor_key_id      UUID         REFERENCES api_keys(id),
    actor_key_prefix  VARCHAR(10),
    team_id           UUID         REFERENCES teams(id),
    resource_type     VARCHAR(50),
    resource_id       VARCHAR(500),
    details           TEXT,
    client_ip         VARCHAR(50),
    outcome           VARCHAR(20),
    occurred_at       TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_ae_event_type  ON audit_events(event_type);
CREATE INDEX idx_ae_actor_key_id ON audit_events(actor_key_id);
CREATE INDEX idx_ae_occurred_at  ON audit_events(occurred_at);
CREATE INDEX idx_ae_team_id      ON audit_events(team_id);
