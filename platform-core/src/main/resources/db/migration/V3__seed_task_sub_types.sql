-- V3 — Seed the task sub-type catalog.
--
-- Only tasks that have MORE than the single implicit DEFAULT sub-type are listed here; every other
-- task falls back to an implicit DEFAULT in code (AgentResolutionService), so we don't enumerate
-- the whole AgentTaskType enum. v1: test-case generation splits into functional / non-functional.

INSERT INTO task_sub_types (task_type, key, label, is_default) VALUES
  ('GENERATE_TEST_CASES', 'FUNCTIONAL',     'Functional',     TRUE),
  ('GENERATE_TEST_CASES', 'NON_FUNCTIONAL', 'Non-functional', FALSE);
