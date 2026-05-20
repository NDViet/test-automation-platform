-- Durable conversation checkpoints for HANDOFF resume strategy (> 24h gaps).
-- Redis stores hot checkpoints (PROMPT_CACHE / COMPRESSED); this table stores cold ones.

CREATE TABLE agent_checkpoints (
    id                  VARCHAR(200) PRIMARY KEY,  -- checkpoint ID (UUID string or hash)
    session_id          UUID         NOT NULL,
    workflow_id         UUID         REFERENCES agent_workflows(id) ON DELETE SET NULL,
    strategy            VARCHAR(20)  NOT NULL,      -- PROMPT_CACHE, COMPRESSED, HANDOFF
    messages_blob_ref   JSONB,                      -- BlobRef JSON: bucket/key/hash/type/size
    compressed_summary  TEXT,
    handoff_blob_ref    JSONB,                      -- BlobRef JSON for structured handoff state
    cache_turn_indices  INT[],
    expires_at          TIMESTAMPTZ,                -- NULL = no expiry for HANDOFF
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_chk_session_id     ON agent_checkpoints(session_id);
CREATE INDEX idx_chk_workflow_id    ON agent_checkpoints(workflow_id) WHERE workflow_id IS NOT NULL;
CREATE INDEX idx_chk_expires_at     ON agent_checkpoints(expires_at) WHERE expires_at IS NOT NULL;
