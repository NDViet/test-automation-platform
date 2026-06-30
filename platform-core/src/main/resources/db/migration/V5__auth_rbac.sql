-- V5 — Authentication & RBAC identity store.
--
-- Adds real users (password auth) and project-scoped role grants. Replaces the team-based
-- team_members model (migrated/dropped later in the cutover). Enforcement is gated by
-- platform.security.enabled (default false) until rollout.

CREATE TABLE users (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    username             VARCHAR(100) NOT NULL UNIQUE,
    email                VARCHAR(200),
    password_hash        VARCHAR(200) NOT NULL,        -- BCrypt
    display_name         VARCHAR(200),
    is_super_admin       BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at        TIMESTAMPTZ
);

-- A role grant for a user at a scope. ORG_ADMIN → scope ORG; PROJECT_ADMIN/TESTER/VIEWER → PROJECT.
-- scope_id is the org id or project id (polymorphic; app-validated). Super-admin is the users flag.
CREATE TABLE user_roles (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role       VARCHAR(20)  NOT NULL,
    scope      VARCHAR(10)  NOT NULL,                  -- ORG | PROJECT
    scope_id   UUID         NOT NULL,
    created_by VARCHAR(200),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_ur_role  CHECK (role IN ('ORG_ADMIN', 'PROJECT_ADMIN', 'TESTER', 'VIEWER')),
    CONSTRAINT chk_ur_scope CHECK (scope IN ('ORG', 'PROJECT')),
    CONSTRAINT uq_user_role UNIQUE (user_id, role, scope, scope_id)
);
CREATE INDEX idx_user_roles_user  ON user_roles(user_id);
CREATE INDEX idx_user_roles_scope ON user_roles(scope, scope_id);
