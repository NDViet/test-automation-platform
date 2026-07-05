-- V24 — Support resuming a generation workflow from the Review Queue with a fresh, context-augmented
-- regeneration round. Records, on the 1:1 generation run, the PR context injected (from stored Impact
-- Analyses) and the effective requirement set for the latest round, plus a counter of how many
-- regenerations have run. All additive/nullable so existing runs are unaffected.
ALTER TABLE ai_generation_runs ADD COLUMN pr_context_json TEXT;
ALTER TABLE ai_generation_runs ADD COLUMN requirement_ids_json TEXT;
ALTER TABLE ai_generation_runs ADD COLUMN regen_round INTEGER NOT NULL DEFAULT 0;
