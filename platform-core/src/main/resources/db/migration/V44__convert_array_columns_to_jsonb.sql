-- test_case_results.tags: text[] → jsonb
ALTER TABLE test_case_results ALTER COLUMN tags DROP DEFAULT;
ALTER TABLE test_case_results ALTER COLUMN tags TYPE JSONB USING to_jsonb(COALESCE(tags, ARRAY[]::text[]));

-- sot_test_plan_items.requirement_ids: uuid[] → jsonb
ALTER TABLE sot_test_plan_items ALTER COLUMN requirement_ids DROP DEFAULT;
ALTER TABLE sot_test_plan_items ALTER COLUMN requirement_ids TYPE JSONB USING to_jsonb(COALESCE(requirement_ids, ARRAY[]::uuid[]));
ALTER TABLE sot_test_plan_items ALTER COLUMN requirement_ids SET DEFAULT '[]'::jsonb;
