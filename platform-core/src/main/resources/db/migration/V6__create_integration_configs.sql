CREATE TABLE integration_configs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id         UUID NOT NULL REFERENCES teams(id),
    tracker_type    VARCHAR(20) NOT NULL,
    base_url        VARCHAR(500),
    project_key     VARCHAR(50),
    config_json     JSONB NOT NULL DEFAULT '{}',
    enabled         BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_integration_team_tracker UNIQUE (team_id, tracker_type)
);

CREATE INDEX idx_ic_team_id ON integration_configs(team_id);
