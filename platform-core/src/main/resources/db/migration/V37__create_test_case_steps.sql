CREATE TABLE test_case_steps (
    id               UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    test_case_id     UUID    NOT NULL REFERENCES platform_test_cases(id) ON DELETE CASCADE,
    step_number      INT     NOT NULL,
    action           TEXT    NOT NULL,
    expected_result  TEXT,
    notes            TEXT,
    UNIQUE (test_case_id, step_number)
);
CREATE INDEX idx_tcs_test_case ON test_case_steps(test_case_id);
