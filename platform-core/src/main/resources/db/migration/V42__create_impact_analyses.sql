CREATE TABLE impact_analyses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL DEFAULT 'Impact Analysis',
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    linked_prs JSONB NOT NULL DEFAULT '[]',
    linked_requirement_ids JSONB NOT NULL DEFAULT '[]',
    suggestions JSONB,
    summary TEXT,
    workflow_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_ia_project ON impact_analyses(project_id);
