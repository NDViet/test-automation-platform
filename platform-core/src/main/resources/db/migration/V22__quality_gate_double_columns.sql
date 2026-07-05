-- Epic B follow-up: quality_gate.min_pass_rate / min_coverage_pct were declared NUMERIC in V14, but the
-- QualityGate entity maps them as Java double, which Hibernate schema-validation expects as float8
-- (double precision). Strict-validating services (portal) fail to boot on the mismatch. Convert the
-- column types to double precision to match the entity. Forward-only — V14 is already shipped.
ALTER TABLE quality_gate ALTER COLUMN min_pass_rate TYPE double precision;
ALTER TABLE quality_gate ALTER COLUMN min_coverage_pct TYPE double precision;
