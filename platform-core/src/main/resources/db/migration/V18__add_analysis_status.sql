-- Add analysis outcome tracking to failure_analyses.
--
-- analysis_status: SUCCESS (AI responded and was parsed) or ERROR (API/parse failure).
-- error_message:   populated on ERROR; stores the exception message for diagnosis.
--
-- Retry logic skips only SUCCESS rows; ERROR rows are retried by the batch job and
-- on-demand "Analyze Now" button.

ALTER TABLE failure_analyses
    ADD COLUMN analysis_status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    ADD COLUMN error_message   TEXT;

-- Existing rows were all successful (errors were never persisted before this migration).
UPDATE failure_analyses SET analysis_status = 'SUCCESS' WHERE analysis_status IS NULL;

CREATE INDEX idx_fa_status ON failure_analyses(analysis_status);
