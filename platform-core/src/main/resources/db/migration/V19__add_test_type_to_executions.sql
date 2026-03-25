-- V19: Add test_type column to test_executions for first-class Functional/Performance classification.
--
-- FUNCTIONAL = JUnit, Cucumber, TestNG, Playwright, Allure, Newman, PLATFORM_NATIVE
-- PERFORMANCE = K6, Gatling, JMeter
-- CONTRACT / SECURITY / ACCESSIBILITY reserved for future parsers.
--
-- Default to 'FUNCTIONAL' for existing rows so analytics queries remain unaffected.

ALTER TABLE test_executions
    ADD COLUMN test_type VARCHAR(20) NOT NULL DEFAULT 'FUNCTIONAL';

CREATE INDEX idx_te_test_type ON test_executions (test_type);
CREATE INDEX idx_te_project_test_type ON test_executions (project_id, test_type);
