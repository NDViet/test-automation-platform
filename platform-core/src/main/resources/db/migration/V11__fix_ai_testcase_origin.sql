-- Restore AI authorship lost on approval.
--
-- created_by is an origin marker constrained to 'AGENT' | 'HUMAN'. A bug in the proposal-acceptance
-- path (ProposalService.acceptEntity) stamped the approving user (e.g. 'admin') as created_by, so
-- any value outside that vocabulary is a bug artifact — and every such row came from an accepted
-- AI-generated proposal. Restore them to 'AGENT'. Idempotent: re-running affects 0 rows.
UPDATE platform_test_cases SET created_by = 'AGENT' WHERE created_by NOT IN ('AGENT', 'HUMAN');
