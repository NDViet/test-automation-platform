-- Allow a project to have multiple integrations of the same type
-- (e.g. two GitHub repos, two Jira projects).
-- The UI controls uniqueness where it matters; the DB no longer enforces 1-per-type.
ALTER TABLE project_integration_configs
    DROP CONSTRAINT IF EXISTS uq_pic_project_type;
