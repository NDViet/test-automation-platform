-- V4 — Record the agent-resolved model id on a generation run, alongside agent_id/task_sub_type
-- (added in V2). Lets the node apply an agent's explicit model override without widening the
-- Hub→Node ContextBundle contract.

ALTER TABLE ai_generation_runs ADD COLUMN resolved_model_id TEXT;
