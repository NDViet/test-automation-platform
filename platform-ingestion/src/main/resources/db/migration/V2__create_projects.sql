CREATE TABLE projects (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id    UUID         NOT NULL REFERENCES teams(id),
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(50)  NOT NULL,
    repo_url   VARCHAR(500),
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_project_team_slug UNIQUE (team_id, slug)
);

CREATE INDEX idx_projects_team_id ON projects(team_id);
