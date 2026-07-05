-- Epic E: test-data datasets. A named, reusable key/value set scoped to a project (+ optional env),
-- feedable into a parameterized case's run matrix. env defaults to '' so uniqueness works with no env.
CREATE TABLE test_dataset (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name       VARCHAR(200) NOT NULL,
    env        VARCHAR(100) NOT NULL DEFAULT '',
    rows       TEXT,                                  -- JSON: {"key":"value", ...} or rows
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_test_dataset UNIQUE (project_id, name, env)
);
CREATE INDEX idx_test_dataset_project ON test_dataset (project_id);
