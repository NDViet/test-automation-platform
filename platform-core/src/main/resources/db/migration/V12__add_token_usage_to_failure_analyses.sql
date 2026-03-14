-- Track AI provider token consumption per analysis.
-- Zero (not NULL) for analyses created before this migration or on error paths.
ALTER TABLE failure_analyses
    ADD COLUMN input_tokens  INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN output_tokens INTEGER NOT NULL DEFAULT 0;

-- Index for aggregate queries in Grafana (tokens over time, tokens by model)
CREATE INDEX idx_fa_model_version ON failure_analyses(model_version);
