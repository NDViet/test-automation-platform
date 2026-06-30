# SPEC — Platform-wide Authentication & RBAC

> Status: **DRAFT — awaiting confirmation**
> Scope: platform-core (domain/migrations), new `platform-security` module, all six services
> (portal, agent, ai, ingestion, analytics, integration), frontend (login + role-gated UI)
> Date: 2026-06-30

---

## 1. Objective

Give the platform **real authentication** and **enforced, scope-aware RBAC across every feature —
UI *and* API** — so nothing leaks based on a user's role or scope. Today identity is a self-typed
`X-Actor` string with no password, and backend services trust it; any caller can impersonate anyone
and any directly-reachable service bypasses checks. This feature closes that hole.

### Roles (project-scoped, replacing the team-based model)
- **Super Admin** — platform owner; bootstrapped on first start. Full control everywhere.
- **Org Admin** — full control of org config and every project in the org.
- **Project Admin** — full control of an assigned project's config (integrations, credentials, role
  grants within the project, deletion).
- **Tester** — CRUD on quality/business features within assigned projects (test cases, suites, runs,
  requirements ops, flaky fixes, generation, **and Agents management**). Not project config.
- **Viewer** — read-only of results/dashboards within assigned projects.

### Target users
QA leads/admins (provision users, assign roles), testers (operate quality features in their
projects), stakeholders (view-only).

### Decisions locked (clarification, 2026-06-30)
1. **Auth:** build full built-in auth (users + password + login + token).
2. **Roles:** replace team-scoping with project-scoping (`ORG_ADMIN→Org Admin`,
   `TEAM_ADMIN→Project Admin`, `TEAM_MEMBER→Tester`, `VIEWER→Viewer`).
3. **Enforcement:** every backend service validates identity + role independently (no portal-only
   trust, no direct-to-service bypass).
4. **Scoping:** roles are assigned **per project**; Org Admin spans the org; Super Admin spans all.

---

## 2. Current state (ground truth)

- **No authentication.** `platform.actor` is typed into the Roles page and sent as `X-Actor`. No
  `users` table, no password, no session, no login screen.
- **Team-based RBAC exists but is advisory.** `RbacService` (`ORG_ADMIN/TEAM_ADMIN/TEAM_MEMBER/
  VIEWER`) over `team_members(user_id, team_id, role)`; enforced only at a few points (e.g.
  `RoleService.grant`) against the self-asserted actor.
- **Services trust the portal.** The portal BFF forwards `X-Actor`; backend services
  (`/hub/...`, `/api/v1/...`) don't authenticate the caller.
- **`PLATFORM_CRED_KEY`** is the credential-encryption passphrase (env) used by `CredentialKeyService`.
  This spec also uses it as the **bootstrap super-admin password**.

**Implication:** RBAC is meaningless without authentication, and "every service enforces" requires a
verifiable token that each service checks — not a client-supplied header.

---

## 3. Authentication design

### 3.1 Identity store (`platform-core`)
`users` — id, username (unique, citext), email, `password_hash` (BCrypt), display_name,
`is_super_admin` (bool), enabled, `must_change_password` (bool), created_at, updated_at, last_login_at.

### 3.2 Bootstrap super-admin
On startup, an idempotent runner: **if `users` is empty**, create one super-admin —
username `${SUPER_ADMIN_USERNAME:admin}`, `password_hash = BCrypt(PLATFORM_CRED_KEY)`,
`is_super_admin = true`, `must_change_password = true`. Fail fast (log + refuse) if
`PLATFORM_CRED_KEY` is unset, so we never create an account with an empty/known password.

### 3.3 Login & token
- `POST /api/portal/auth/login {username, password}` → verify BCrypt → issue a **JWT** (HS256,
  signed with **`PLATFORM_JWT_SECRET`** env var), returned as an **httpOnly, Secure, SameSite cookie**
  (`platform_token`) + a small JSON body (display name, must-change flag).
- **`PLATFORM_JWT_SECRET`** is **required in prod** (the same value across all services). In **dev**
  it may be set to **persist sessions across restarts**; if left unset in dev, a random secret is
  generated per boot (so dev sessions reset on restart — never use an auto-generated secret in prod).
- JWT claims: `sub` (userId), `username`, `super` (bool), `iat`, `exp` (e.g. 8h). **Roles are NOT
  baked into the token** — they are resolved fresh per request from the DB so grants/revocations
  take effect immediately.
- `POST /api/portal/auth/logout` clears the cookie. `GET /api/portal/auth/me` returns the current
  user + effective roles. `POST /api/portal/auth/change-password` (self).
- Refresh: short-lived access token + sliding renewal on activity (or a refresh cookie). v1 may use
  a single 8h token with renewal-on-request.

### 3.4 Service-to-service trust
The portal forwards the **same JWT** (`Authorization: Bearer <token>`) to backend services. **Every
service** validates the JWT signature/expiry with the shared `PLATFORM_JWT_SECRET` and derives the
user — the `X-Actor` header is retired as a trust source (a request without a valid token is 401).
Internal jobs (Kafka consumers, schedulers) run as a system principal, exempt from user RBAC.

---

## 4. Authorization model

### 4.1 Roles & scopes
`Role ∈ {ORG_ADMIN, PROJECT_ADMIN, TESTER, VIEWER}` (super-admin is the `is_super_admin` flag, not a
row). A grant is `(user, role, scope, scope_id)` where `scope ∈ {ORG, PROJECT}`:
- `ORG_ADMIN` → scope ORG.
- `PROJECT_ADMIN | TESTER | VIEWER` → scope PROJECT.

`user_roles` table — id, user_id, role, scope, scope_id, created_by, created_at;
unique `(user_id, role, scope, scope_id)`. (Replaces `team_members`.)

### 4.2 Permission semantics (capability tiers, per scope)
Ordered tiers: **VIEW < OPERATE < ADMIN_PROJECT < ADMIN_ORG < SUPER**. A user's tier for a project =
max of: super → SUPER; org-admin of its org → ADMIN_ORG; project-admin → ADMIN_PROJECT;
tester → OPERATE; viewer → VIEW.

| Capability (examples) | Min tier |
|---|---|
| Read results/dashboards/analyses/executions/reports | **VIEW** |
| CRUD test cases/suites/runs, requirements ops, flaky fix, **agents/skills/prompt-templates/generation**, run execution | **OPERATE** (Tester) |
| Project config: integrations, credentials, GitHub config, mapping rules, role grants *within the project*, delete project entities | **ADMIN_PROJECT** |
| Org config: create/delete projects & orgs, org-level settings, role grants at org scope | **ADMIN_ORG** |
| Platform: manage users; **AI/LiteLLM gateway settings**; **ADO structure import / onboarding into the platform** (provisioning orgs/projects from Azure DevOps); super-admin ops | **SUPER** |

### 4.3 The permission check
A single `PermissionEvaluator.require(user, Capability, scope, scopeId)` (in `platform-security`),
backed by a `RoleResolver` reading `user_roles`. Capabilities are an enum mapped to a min tier;
`scopeId` is the project (or org) the request targets. Returns 403 on failure. Reused identically by
every service so the matrix has one source of truth.

---

## 5. Enforcement architecture (every service)

```
browser ─cookie JWT─> portal BFF ─Bearer JWT─> service (agent/ai/ingestion/analytics/integration)
                       (authn)                   (authn: validate JWT  +  authz: PermissionEvaluator)
```

- **New `platform-security` module** (depends on platform-core): a Spring Security
  `OncePerRequestFilter` that validates the JWT → populates an `AuthenticatedUser` principal; the
  `PermissionEvaluator` + `RoleResolver`; and a `@RequireCapability(capability, scopeParam)` method
  annotation (custom `@PreAuthorize`) so controllers declare requirements declaratively. Every
  service adds the dependency and a `SecurityFilterChain` that requires authentication on
  `/hub/**`, `/api/**` (health/actuator open).
- Controllers either annotate (`@RequireCapability(OPERATE, project="projectId")`) or call
  `evaluator.require(...)` (matching the existing explicit-guard style, e.g. `AgentRbacGuard`).
- The existing **`AgentRbacGuard`** is folded into this model (its feature flag is removed once the
  platform-wide gate is live).
- The portal additionally **route-guards the UI** (hide/disable actions by role) — defense in depth,
  not the security boundary.

---

## 6. Migration & compatibility

- **New tables:** `users`, `user_roles` (Flyway). Keep `team_members` until migrated, then drop.
- **Data migration:** convert `team_members` → `user_roles` (`TEAM_ADMIN`→`PROJECT_ADMIN` with the
  team's `project_id`; `TEAM_MEMBER`→`TESTER`; `ORG_ADMIN`→org `ORG_ADMIN`; `VIEWER`→project/org
  `VIEWER`). Existing `user_id` strings become `users` rows **without passwords** → flagged
  `must_change_password` and disabled until an admin sets a password / invites them. (No silent
  password-less logins.)
- **`X-Actor` retirement:** controllers that took `X-Actor` now derive the actor from the verified
  principal; the header is ignored for trust. Audit fields (`created_by`) use the authenticated user.
- **Roll-out switch:** a single `platform.security.enabled` flag (default **false** until the
  feature is verified end-to-end) so the platform isn't locked out mid-migration; flip to true to
  enforce. (Same pattern as the current `agent.rbac.enabled`.)

---

## 7. APIs (new)

**Auth (portal):** `POST /auth/login`, `POST /auth/logout`, `GET /auth/me`,
`POST /auth/change-password`.
**User admin (super/org-admin):** `GET/POST/PUT/DELETE /admin/users`, `POST /admin/users/{id}/reset-password`,
`PUT /admin/users/{id}/enabled`.
**Role grants (replaces `/rbac/members`):** `GET /rbac/roles?scope&scopeId`,
`POST /rbac/roles` `{userId, role, scope, scopeId}` (grantor must hold ≥ that tier at that scope),
`DELETE /rbac/roles/{id}`. Cannot remove the last Super Admin / org's last Org Admin.

---

## 8. Frontend

- **Login screen** (unauthenticated route); on success store nothing sensitive (token is httpOnly
  cookie) — keep `auth/me` in a React context. Remove the "type your actor" box on the Roles page.
- **Forced password change** screen when `must_change_password`.
- **Role-gated UI:** a `useAuth()` context + `<Can capability scope>` guard hides/disables nav items
  and buttons (e.g. Viewer sees read-only; Tester sees Agents but not project Integrations; Project
  Admin sees project settings; Org Admin sees org settings + AI gateway). 401 → redirect to login;
  403 → friendly "not permitted".
- **User management page** (super/org-admin): list users, create, enable/disable, reset password,
  grant/revoke roles per scope.

---

## 9. Data model (new tables)

```
users(id, username UNIQUE, email, password_hash, display_name, is_super_admin,
      enabled, must_change_password, created_at, updated_at, last_login_at)
user_roles(id, user_id→users, role, scope ['ORG'|'PROJECT'], scope_id,
           created_by, created_at, UNIQUE(user_id, role, scope, scope_id))
```

---

## 10. Project structure (where code goes)

```
platform-core/
  domain/User.java, UserRole.java        repository/UserRepository, UserRoleRepository
  resources/db/migration/V5__auth_rbac.sql  (+ V6 data migration, V7 drop team_members)
platform-security/                       # NEW shared module (all services depend on it)
  jwt/{JwtService, JwtAuthFilter, AuthenticatedUser}
  authz/{Capability, Tier, RoleResolver, PermissionEvaluator, RequireCapability + aspect}
  config/PlatformSecurityAutoConfiguration  (SecurityFilterChain, gated by platform.security.enabled)
platform-portal/
  api/AuthController, AdminUserController, (RoleController updated)
  frontend/src/pages/{LoginPage, UsersPage, ChangePasswordPage}, context/AuthContext, components/Can
<each service> — add platform-security dep + SecurityFilterChain; controllers annotate @RequireCapability
```

---

## 11. Code style
- Reuse the platform's conventions: constructor injection, `ResponseStatusException` for 401/403,
  Flyway for schema, records for DTOs. One `Capability` enum + one `PermissionEvaluator` — never
  re-implement role logic per service.
- Passwords: BCrypt (spring-security-crypto), never logged, never returned. JWT secret + cred key
  from env only.
- Keep the security filter additive and **fail-closed only when `platform.security.enabled`**.

## 12. Testing strategy
- **Unit:** `PermissionEvaluator` tier math (every role × capability × scope, allow/deny);
  `JwtService` sign/verify/expiry/tamper; bootstrap runner (empty-users → creates super-admin,
  non-empty → no-op; missing cred key → fail).
- **Integration (per service):** an authenticated request with insufficient role → 403; no/invalid
  token → 401; sufficient role → 200; super-admin → always allowed.
- **Cross-cutting:** a matrix test asserting each role can/can't reach representative endpoints
  (Viewer can read but not CRUD; Tester can CRUD agents but not project integrations; Project Admin
  can configure the project but not the org; Org Admin can't manage platform users).
- **Migration:** `team_members` → `user_roles` mapping; password-less migrated users are disabled.
- **Frontend:** login flow, forced password change, role-gated nav/actions; 401/403 handling.

## 13. Boundaries

**Always**
- Authenticate first, then authorize; derive the actor from the verified token, never the client.
- Fail-closed once enabled; default-deny for unmapped endpoints.
- Keep one capability matrix; super-admin and the last org-admin are protected from self-lockout.
- Hash passwords (BCrypt); secrets from env; tokens httpOnly.

**Ask first**
- The exact capability→tier mapping for ambiguous endpoints (e.g. is "trigger CI run" Tester or
  Project Admin?).
- Cookie domain for multi-host deploys.

**Never**
- Never trust `X-Actor` (or any client header) for identity once auth ships.
- Never store/print plaintext passwords or the JWT secret; never bake roles into the token such that
  revocation is delayed.
- Never leave a service unauthenticated on `/hub/**` or `/api/**` when enforcement is on.
- Never overwrite the root `SPEC.md` or other features' specs.

---

## 14. Acceptance criteria
- **AC1** First boot with empty `users` provisions a Super Admin (`admin` / `PLATFORM_CRED_KEY`),
  forced to change password on first login; subsequent boots are no-ops.
- **AC2** Login issues an httpOnly JWT cookie; `/auth/me` returns the user + effective roles; logout
  clears it. No password is ever returned or logged.
- **AC3** Every backend service rejects requests with no/invalid token (401) and insufficient role
  (403) — verified by hitting a service directly, not only via the portal.
- **AC4** Capability matrix holds: Viewer read-only; Tester CRUDs quality features incl. **Agents**
  but not project config; Project Admin manages the project; Org Admin manages the org; Super Admin
  everything — UI and API.
- **AC5** Roles are per assigned project; a Tester in project A cannot act in project B.
- **AC6** Grants/revocations take effect immediately (roles resolved per request, not from the token).
- **AC7** `team_members` migrated to `user_roles`; migrated users are disabled until a password is set.
- **AC8** `platform.security.enabled=false` preserves today's behavior (no lockout) for staged rollout.

## 15. Phased delivery
1. **Identity core** — `users`/`user_roles` tables, `User`/`UserRole` domain, bootstrap super-admin,
   `platform-security` module (`JwtService`, `Capability`/`Tier`, `PermissionEvaluator`) + unit tests.
2. **Auth endpoints + portal login** — login/logout/me/change-password, httpOnly cookie, login UI,
   forced password change.
3. **Enforce in one service end-to-end** (portal + agent) behind `platform.security.enabled`;
   `@RequireCapability` on its endpoints; fold in `AgentRbacGuard`. Verify direct-call 401/403.
4. **Roll enforcement to remaining services** (ai, ingestion, analytics, integration) + capability
   annotations per endpoint.
5. **Role-gated UI** (`useAuth`, `<Can>`, hide/disable) + **user management page**.
6. **Migrate** `team_members`→`user_roles`, retire `X-Actor` trust, flip the flag on, drop old table.

## 16. Resolved decisions (defaults accepted 2026-06-30)
- **Token:** single **8h sliding token** (renew-on-activity); no separate refresh token in v1. Cookie
  domain remains an ops/deploy detail to confirm at rollout.
- **`PLATFORM_JWT_SECRET`** env var: required in prod (shared across all services); dev may set it to
  persist sessions across restarts, otherwise a per-boot random secret is used.
- **AI/LiteLLM gateway settings → Super Admin only** (SUPER tier).
- **ADO structure import / platform onboarding → Super Admin only** (SUPER tier).
- **User onboarding:** admin-created only; no email-invite flow in v1.
- **Audit:** application logs only in v1; no separate persistent authz audit trail.
