CREATE TABLE failure_analyses (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    test_id              VARCHAR(500)     NOT NULL,
    project_id           UUID             NOT NULL REFERENCES projects(id),
    test_case_result_id  UUID             REFERENCES test_case_results(id),
    category             VARCHAR(30)      NOT NULL,
    confidence           DOUBLE PRECISION NOT NULL,
    root_cause           VARCHAR(500),
    detailed_analysis    TEXT,
    suggested_fix        TEXT,
    is_flaky_candidate   BOOLEAN          NOT NULL DEFAULT false,
    affected_component   VARCHAR(200),
    model_version        VARCHAR(50),
    analysed_at          TIMESTAMP        NOT NULL DEFAULT now()
);

CREATE INDEX idx_fa_test_id     ON failure_analyses(test_id);
CREATE INDEX idx_fa_project_id  ON failure_analyses(project_id);
CREATE INDEX idx_fa_category    ON failure_analyses(category);
CREATE INDEX idx_fa_analysed_at ON failure_analyses(analysed_at);
