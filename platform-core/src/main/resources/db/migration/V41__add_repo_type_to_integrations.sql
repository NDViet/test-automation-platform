-- Classify GitHub repos linked to a project.
-- GENERAL    = default (unclassified, works as before)
-- CODEBASE   = source code being tested (read reference for test generation context)
-- TEST_AUTOMATION = where generated test code + PRs go (write target)
ALTER TABLE project_integration_configs
    ADD COLUMN IF NOT EXISTS repo_type VARCHAR(20) NOT NULL DEFAULT 'GENERAL';
