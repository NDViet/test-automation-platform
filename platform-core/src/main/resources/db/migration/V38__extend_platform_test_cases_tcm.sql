ALTER TABLE platform_test_cases
    ADD COLUMN IF NOT EXISTS suite_id                    UUID REFERENCES test_suites(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS status                      VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN IF NOT EXISTS priority                    VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM',
    ADD COLUMN IF NOT EXISTS description                 TEXT,
    ADD COLUMN IF NOT EXISTS preconditions               TEXT,
    ADD COLUMN IF NOT EXISTS expected_result             TEXT,
    ADD COLUMN IF NOT EXISTS source_requirement_id       UUID REFERENCES platform_requirements(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS automation_status           VARCHAR(20)  NOT NULL DEFAULT 'NOT_STARTED',
    ADD COLUMN IF NOT EXISTS automation_pr_url           TEXT,
    ADD COLUMN IF NOT EXISTS automation_github_config_id UUID;

CREATE INDEX idx_ptc_suite ON platform_test_cases(project_id, suite_id);
CREATE INDEX idx_ptc_status ON platform_test_cases(project_id, status);
