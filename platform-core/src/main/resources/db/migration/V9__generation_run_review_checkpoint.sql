-- V9 — Persist the conversation checkpoint captured when a generation run finishes, so the user can
-- refine the proposed test cases by continuing that same conversation (the refine flow resumes from
-- here, and updates it after each refinement round).
ALTER TABLE ai_generation_runs ADD COLUMN review_checkpoint_id TEXT;
