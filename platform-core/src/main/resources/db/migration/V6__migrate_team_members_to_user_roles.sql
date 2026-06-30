-- V6: migrate the legacy team_members model into the new users + user_roles RBAC model.
--
-- Mapping (per spec §F1):
--   ORG_ADMIN   -> ORG_ADMIN
--   TEAM_ADMIN  -> PROJECT_ADMIN (the team's project)
--   TEAM_MEMBER -> TESTER
--   VIEWER      -> VIEWER
--
-- Grants with a team_id are project-scoped (the team's project). Legacy grants with no team_id are
-- org-wide; they carry no org reference, so they are mapped to the organization only when the
-- platform has exactly one org (true for an ADO-bootstrapped install). RoleResolver honors org-scoped
-- TESTER/VIEWER as operate/read across every project in the org.
--
-- Migrated users are created DISABLED and must set a password before they can sign in (no
-- password-less login): the password hash is a non-verifiable placeholder, never a real credential.

-- 1) One user per distinct legacy user_id (username = the legacy identifier, e.g. an email),
--    skipping anyone who already has an account.
INSERT INTO users (id, username, email, password_hash, display_name,
                   is_super_admin, enabled, must_change_password, created_at, updated_at)
SELECT gen_random_uuid(),
       tm.user_id,
       CASE WHEN tm.user_id LIKE '%@%' THEN tm.user_id END,
       '!locked-no-password',
       tm.user_id,
       false, false, true, now(), now()
FROM (SELECT DISTINCT user_id FROM team_members) tm
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.username = tm.user_id);

-- 2) Project-scoped grants (team_id present -> the team's project).
INSERT INTO user_roles (id, user_id, role, scope, scope_id, created_by, created_at)
SELECT gen_random_uuid(),
       u.id,
       CASE tm.role
            WHEN 'TEAM_ADMIN'  THEN 'PROJECT_ADMIN'
            WHEN 'TEAM_MEMBER' THEN 'TESTER'
            WHEN 'VIEWER'      THEN 'VIEWER'
            WHEN 'ORG_ADMIN'   THEN 'PROJECT_ADMIN'
       END,
       'PROJECT', t.project_id, 'v6-migration', now()
FROM team_members tm
JOIN users u ON u.username = tm.user_id
JOIN teams t ON t.id = tm.team_id
WHERE tm.team_id IS NOT NULL
ON CONFLICT (user_id, role, scope, scope_id) DO NOTHING;

-- 3) Org-wide grants (no team_id -> the single org, if exactly one exists).
INSERT INTO user_roles (id, user_id, role, scope, scope_id, created_by, created_at)
SELECT gen_random_uuid(),
       u.id,
       CASE tm.role
            WHEN 'ORG_ADMIN'   THEN 'ORG_ADMIN'
            WHEN 'TEAM_MEMBER' THEN 'TESTER'
            WHEN 'VIEWER'      THEN 'VIEWER'
       END,
       'ORG', (SELECT id FROM organizations LIMIT 1), 'v6-migration', now()
FROM team_members tm
JOIN users u ON u.username = tm.user_id
WHERE tm.team_id IS NULL
  AND tm.role IN ('ORG_ADMIN', 'TEAM_MEMBER', 'VIEWER')
  AND (SELECT count(*) FROM organizations) = 1
ON CONFLICT (user_id, role, scope, scope_id) DO NOTHING;
