-- Seed a demo team and project so the platform is usable out of the box.
-- ON CONFLICT ... DO NOTHING makes this safe to run against an existing database.

INSERT INTO teams (id, name, slug)
VALUES ('11111111-1111-1111-1111-111111111111', 'Platform Demo', 'platform-demo')
ON CONFLICT (id) DO NOTHING;

INSERT INTO projects (id, team_id, name, slug)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    'Demo Project',
    'demo-project'
)
ON CONFLICT (id) DO NOTHING;
