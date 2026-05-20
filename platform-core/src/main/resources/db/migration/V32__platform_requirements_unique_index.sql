-- Replace partial index with unique partial index so ON CONFLICT works for upserts.
-- External IDs (JIRA-123, LIN-456, etc.) are unique per project; NULL is allowed for
-- manually-created requirements that have no external tracking ticket.
DROP INDEX IF EXISTS idx_req_external;

CREATE UNIQUE INDEX idx_req_external_unique
    ON platform_requirements (project_id, external_id)
    WHERE external_id IS NOT NULL;
