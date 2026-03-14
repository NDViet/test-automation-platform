-- Seed the teams and projects used by the bundled test modules so they work
-- out-of-the-box on a fresh docker compose up without any manual setup.
-- ON CONFLICT ... DO NOTHING makes every INSERT idempotent.

-- ── saucedemo-tests ──────────────────────────────────────────────────────────
INSERT INTO teams (id, name, slug)
VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'SauceDemo Team', 'team-saucedemo')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO projects (id, team_id, name, slug)
VALUES (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    (SELECT id FROM teams WHERE slug = 'team-saucedemo'),
    'SauceDemo E2E',
    'proj-saucedemo'
)
ON CONFLICT (id) DO NOTHING;

-- ── onboard / theinternet-tests ───────────────────────────────────────────────
INSERT INTO teams (id, name, slug)
VALUES ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'The Internet Team', 'team-the-internet')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO projects (id, team_id, name, slug)
VALUES (
    'dddddddd-dddd-dddd-dddd-dddddddddddd',
    (SELECT id FROM teams WHERE slug = 'team-the-internet'),
    'The Internet Tests',
    'proj-the-internet'
)
ON CONFLICT (id) DO NOTHING;
