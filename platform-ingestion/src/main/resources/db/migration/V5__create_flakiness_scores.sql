CREATE TABLE flakiness_scores (
    id              UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    test_id         VARCHAR(500)   NOT NULL,
    project_id      UUID           NOT NULL REFERENCES projects(id),
    score           DECIMAL(5, 4)  NOT NULL,
    classification  VARCHAR(20)    NOT NULL,
    total_runs      INT            NOT NULL,
    failure_count   INT            NOT NULL,
    failure_rate    DECIMAL(5, 4)  NOT NULL,
    last_failed_at  TIMESTAMP,
    last_passed_at  TIMESTAMP,
    computed_at     TIMESTAMP      NOT NULL DEFAULT now(),
    CONSTRAINT uq_flakiness_test_project UNIQUE (test_id, project_id)
);

CREATE INDEX idx_flakiness_project_score ON flakiness_scores(project_id, score DESC);
CREATE INDEX idx_flakiness_score         ON flakiness_scores(score DESC);
