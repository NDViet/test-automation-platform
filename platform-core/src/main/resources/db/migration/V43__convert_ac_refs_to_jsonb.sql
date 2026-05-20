ALTER TABLE platform_test_cases
    ALTER COLUMN ac_refs DROP DEFAULT;

ALTER TABLE platform_test_cases
    ALTER COLUMN ac_refs TYPE JSONB USING to_jsonb(ac_refs);

ALTER TABLE platform_test_cases
    ALTER COLUMN ac_refs SET DEFAULT '[]'::jsonb;
