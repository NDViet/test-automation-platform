-- Track all requirement linkages, automation workflow origin, and impact analysis attribution
ALTER TABLE platform_test_cases
    ADD COLUMN IF NOT EXISTS linked_requirement_ids JSONB NOT NULL DEFAULT '[]',
    ADD COLUMN IF NOT EXISTS automation_workflow_id  UUID,
    ADD COLUMN IF NOT EXISTS last_updated_by_analysis_id UUID,
    ADD COLUMN IF NOT EXISTS updated_by             VARCHAR(30) NOT NULL DEFAULT 'HUMAN';

-- Seed linked_requirement_ids from the existing single sourceRequirementId
UPDATE platform_test_cases
SET linked_requirement_ids = jsonb_build_array(source_requirement_id::text)
WHERE source_requirement_id IS NOT NULL;

-- Seed updated_by from created_by
UPDATE platform_test_cases SET updated_by = created_by;
