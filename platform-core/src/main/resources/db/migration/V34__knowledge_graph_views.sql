-- Materialised views for graph traversal and coverage gap reporting.

-- 1. Full requirement ancestry chain (recursive CTE over ltree/parent_id)
CREATE VIEW sot_requirement_ancestry AS
WITH RECURSIVE ancestry AS (
    SELECT id, external_id, title, parent_id, depth,
           COALESCE(CAST(path AS TEXT), external_id) AS full_path
    FROM   platform_requirements
    WHERE  parent_id IS NULL
    UNION ALL
    SELECT r.id, r.external_id, r.title, r.parent_id, r.depth,
           a.full_path || '.' || COALESCE(r.external_id, r.id::TEXT)
    FROM   platform_requirements r
    JOIN   ancestry a ON a.id = r.parent_id
)
SELECT * FROM ancestry;

-- 2. Coverage gaps: requirements in a release that have no test cases
CREATE VIEW sot_test_plan_coverage_gaps AS
SELECT
    rr.release_id,
    r.project_id,
    r.id          AS requirement_id,
    r.external_id,
    r.title,
    r.issue_type,
    COUNT(e.id)   AS test_case_count
FROM sot_release_requirements rr
JOIN platform_requirements r ON r.id = rr.requirement_id
LEFT JOIN platform_traceability_edges e
       ON e.to_id    = r.id
      AND e.to_tier  = 'REQUIREMENT'
      AND e.edge_type = 'COVERED_BY'
GROUP BY rr.release_id, r.project_id, r.id, r.external_id, r.title, r.issue_type
HAVING COUNT(e.id) = 0;
