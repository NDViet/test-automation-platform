-- Epic D: test-case version history. A revision snapshots title/status/steps/tags as JSON on each
-- save (update/approve/reject) so a case's history can be inspected and diffed.
CREATE TABLE test_case_revision (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    test_case_id UUID         NOT NULL REFERENCES platform_test_cases(id) ON DELETE CASCADE,
    project_id   UUID         NOT NULL,
    version      INT          NOT NULL,
    snapshot     TEXT         NOT NULL,     -- JSON: title/status/priority/description/steps/tags/...
    changed_by   VARCHAR(100),
    change_kind  VARCHAR(30),               -- UPDATE | APPROVE | REJECT
    changed_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_tcr_case_version ON test_case_revision (test_case_id, version DESC);
