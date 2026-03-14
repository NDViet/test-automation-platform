CREATE TABLE api_keys (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(200)  NOT NULL,
    key_hash      VARCHAR(64)   NOT NULL UNIQUE,
    key_prefix    VARCHAR(10)   NOT NULL,
    team_id       UUID          NOT NULL REFERENCES teams(id),
    revoked       BOOLEAN       NOT NULL DEFAULT false,
    expires_at    TIMESTAMP,
    last_used_at  TIMESTAMP,
    created_at    TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_ak_key_hash ON api_keys(key_hash);
CREATE        INDEX idx_ak_team_id  ON api_keys(team_id);
