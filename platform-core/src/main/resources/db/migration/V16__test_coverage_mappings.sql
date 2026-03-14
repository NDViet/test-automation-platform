-- Test Impact Analysis: tracks which test cases cover which production classes.
-- Populated from @AffectedBy annotations (Java SDK) and coveredModules (JS SDK).
-- Used by TestImpactService to answer: "given these changed files, which tests should run?"

CREATE TABLE test_coverage_mappings (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id    UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    test_case_id  TEXT        NOT NULL,  -- matches TestCaseResult.test_id
    class_name    TEXT        NOT NULL,  -- fully qualified: com.example.PaymentService
    method_name   TEXT,                  -- optional method-level granularity
    last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_coverage UNIQUE (project_id, test_case_id, class_name)
);

CREATE INDEX idx_coverage_project_class ON test_coverage_mappings (project_id, class_name);
CREATE INDEX idx_coverage_project_test  ON test_coverage_mappings (project_id, test_case_id);
