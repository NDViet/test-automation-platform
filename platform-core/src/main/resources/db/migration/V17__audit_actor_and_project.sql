-- Epic D: extend audit to portal users + project scope. The aspect captured only API-key actors;
-- portal (JWT) callers now record their user id/username, and events carry a project for scoped views.
ALTER TABLE audit_events ADD COLUMN actor_user_id  UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE audit_events ADD COLUMN actor_username VARCHAR(100);
ALTER TABLE audit_events ADD COLUMN project_id     UUID;

CREATE INDEX idx_ae_project_id ON audit_events (project_id);
CREATE INDEX idx_ae_actor_user_id ON audit_events (actor_user_id);
