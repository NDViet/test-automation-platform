-- Editable free-text instructions fed to the Impact Analysis AI on each run.
ALTER TABLE impact_analyses ADD COLUMN custom_prompt text;
