# TODO — Platform-wide Authentication & RBAC

Source spec: `spec/platform-rbac-spec.md` · Plan: `tasks/plan.md`.
Convention: TDD per task (RED → GREEN → regression → build), one commit per task. `[ ]` open · `[x]` done.
Enforcement stays behind `platform.security.enabled` (default **false**) until Phase F cutover.

---

## Phase A — Security core (foundation)

- [x] **A1: Flyway `V5__auth_rbac.sql` + identity domain + bootstrap super-admin**
  - `users` + `user_roles` tables; `User`/`UserRole` entities + repos. Bootstrap runner: empty
    `users` ⇒ create super-admin (`${SUPER_ADMIN_USERNAME:admin}`, BCrypt(`PLATFORM_CRED_KEY`),
    `is_super_admin`, `must_change_password`); fail fast if `PLATFORM_CRED_KEY` unset; idempotent.
  - **AC:** migration applies clean; empty DB → one super-admin; non-empty → no-op; missing cred key
    → startup fails; password is BCrypt (never plaintext).
  - **Verify:** migration dry-run; `BootstrapAdminRunnerTest` (3 cases).

- [x] **A2: `platform-security` module — JWT + capability model (pure)**
  - New module (root pom) depending on platform-core. `JwtService` (HS256 sign/verify/expiry,
    `PLATFORM_JWT_SECRET`), `AuthenticatedUser`, `Capability` enum + `Tier`, `RoleResolver` (reads
    `user_roles`), `PermissionEvaluator.require(user, capability, scope, scopeId)`.
  - **AC:** tier math for every role × capability × scope (allow/deny incl. SUPER-always,
    AI-gateway/ADO-import → SUPER, Tester=OPERATE, Viewer=VIEW); JWT verify rejects tampered/expired.
  - **Verify:** `PermissionEvaluatorTest`, `JwtServiceTest` green.

- [x] **A3: Security filter + `@RequireCapability` (flag-gated auto-config)**
  - `JwtAuthFilter` → populates principal; `SecurityFilterChain` requiring auth on `/hub/**`,`/api/**`
    (actuator open) **only when `platform.security.enabled`**; `@RequireCapability(cap, scopeParam)`
    method annotation + aspect calling the evaluator.
  - **AC:** valid token authenticates; missing/invalid → 401; annotated method with insufficient role
    → 403; flag off → no enforcement (pass-through).
  - **Verify:** module slice test (mock filter chain) for 401/403/allow + flag-off pass-through.

- [x] **CHECKPOINT A** — module builds; bootstrap + evaluator + JWT tests green; no service gated.

---

## Phase B — Auth endpoints + portal login

- [x] **B1: `AuthController` (login/logout/me/change-password) + JWT cookie**
  - Portal endpoints; login verifies BCrypt → sets httpOnly `platform_token` cookie; `/auth/me`
    returns user + effective roles (resolved live); `/auth/change-password` (self) clears
    `must_change_password`.
  - **AC:** good creds → cookie + body; bad creds → 401; `/me` reflects grants; password never
    returned/logged; `last_login_at` updated.
  - **Verify:** `AuthControllerTest` (login ok/bad, me, change-password).

- [x] **B2: Login + forced-change UI; remove "type actor" box**
  - `LoginPage`, `AuthContext` (`useAuth`), `ChangePasswordPage`; unauthenticated → login;
    `must_change_password` → change screen; remove the self-typed actor input on the Roles page.
  - **AC:** unauth redirected to login; bootstrap admin forced to change password; app loads after.
  - **Verify:** `npx tsc -b`; browser login flow.

- [x] **CHECKPOINT B** — user logs in; bootstrap super-admin forced to change password; `/me` works.

---

## Phase C — Enforce in portal + agent (first enforced E2E)

- [x] **C1: Gate platform-agent endpoints with `@RequireCapability`; fold in `AgentRbacGuard`**
  - Add `platform-security` dep + `SecurityFilterChain` to platform-agent; annotate agent/task-agent
    + generation endpoints (CRUD → OPERATE/ADMIN per matrix); replace `AgentRbacGuard` with the
    evaluator (remove its standalone flag).
  - **AC (flag on):** direct call to platform-agent w/o token → 401; Tester CRUDs agents; Viewer →
    403; project A Tester can't act in project B.
  - **Verify:** integration tests (401/403/allow) + direct `curl` to :8086.

- [x] **C2: Portal forwards the user JWT to backend services**
  - Portal BFF attaches `Authorization: Bearer <token>` (from the cookie) on `agentClient` (and the
    shared client config); actor derived from the verified token, not `X-Actor`.
  - **AC:** portal→agent calls carry the token and pass; unauthenticated portal session → 401.
  - **Verify:** integration through the portal; agent logs show authenticated principal.

- [x] **CHECKPOINT C** — login→portal→agent enforced behind the flag; direct-call bypass blocked;
  role checks hold.

---

## Phase D — Roll enforcement to remaining services

- [x] **D1: platform-ai — filter + capability annotations (AI-gateway → SUPER)**
  - **AC:** AI settings/gateway + model-fetch require SUPER; analysis reads require VIEW; 401/403 as
    matrix. **Verify:** service integration tests + direct curl.
  - Done: gateway settings (GET/PUT/test/models) + batch run-now → `MANAGE_AI_GATEWAY`; analysis
    reads → VIEW_RESULTS(projectId); analyse + scoped-override → OPERATE/MANAGE_PROJECT.
    `AiRbacAnnotationTest` (5) green; module 24/0. Beans auto-picked via `com.platform.*` scan.

- [x] **D2: platform-ingestion — JWT alongside existing API-key; annotations (ADO-import → SUPER)**
  - Coexist with `ApiKeyAuthFilter` (service calls) + JWT (user calls); ADO onboarding/structure
    import → SUPER; project ops → OPERATE/ADMIN_PROJECT.
  - **AC:** service-key path still works; user ADO-import requires SUPER; portal→ingestion unbroken.
  - Done: `@RequireCapability` extended to class-level (method overrides); `IngestionRbacConfig`
    imports the capability beans WITHOUT the second filter chain; `SecurityConfig` adds
    `JwtCookieAuthFilter` into the existing chain (X-API-Key + JWT both authenticate). ADO onboard +
    cred-key → SUPER; 20 project controllers gated (VIEW/OPERATE/MANAGE_PROJECT). `RequireCapability
    AspectTest` (3) + `IngestionRbacAnnotationTest` (5) green; module 115/0; API-key tests still pass.
  - **Deferred (flag-off, no runtime impact):** credential/org/project-CRUD + execution-query
    controllers keyed by a non-project id (or create-without-scope) left authenticated-only — finish
    granular scoping in Phase E user-admin.

- [x] **D3: platform-analytics + platform-integration — annotations**
  - Mostly VIEW (dashboards/results) with OPERATE where they mutate.
  - Done: analytics project endpoints → VIEW_RESULTS(projectId); flakiness recompute →
    OPERATE_QUALITY; org rollups + run/result-keyed logs/traces ungated. `AnalyticsRbacAnnotationTest`
    (3) green; module 40/0. **platform-integration has no HTTP endpoints — nothing to gate.**

- [x] **CHECKPOINT D** — all services enforce (flag-gated); capability matrix covered by
  `PermissionEvaluatorTest` + per-service annotation tests.
- [x] **Deploy-verified A–D** (docker compose, all 6 services, `platform.security.enabled=true`):
  migration V5 applies; bootstrap super-admin + login/me/change-password; unauth→403 on every
  service; super allow; Viewer/Tester/cross-project tier denials; ADO/AI-gateway SUPER gates;
  ingestion X-API-Key ⊕ JWT coexistence. Fixed two integration bugs found only at runtime — portal
  ObjectMapper/backfill boot failure and the ERROR-dispatch 403-masking (commit b0efad4). Compose
  wiring (shared JWT secret, portal DB, security toggle) added.

---

## Phase E — Role-gated UI + user administration

- [x] **E1: `useAuth` + `<Can>` — hide/disable by role**
  - Nav + action gating: Viewer read-only; Tester sees Agents/quality features, not project
    Integrations/credentials; Project Admin sees project settings; Org Admin sees org settings;
    AI-gateway + ADO-import only for Super Admin. 401→login, 403→friendly notice.
  - **AC:** each role sees the correct surface; forbidden actions hidden/disabled.
  - Done: `lib/auth.ts` (client Tier/Capability mirror of platform-security), `<Can>`/`useCan`/
    `RequireCap`/`Forbidden` (components/Can.tsx); Sidebar admin + project-settings items gated by
    capability (empty sections hidden); sensitive global routes (AI→SUPER, agents→OPERATE,
    org/roles/integrations/mapping/api-keys→MANAGE_ORG) wrapped in `RequireCap`; global query/mutation
    error handler → 401 re-auth, 403 `ForbiddenToast`. **Verify:** `npx tsc -b` green (no JS runner;
    capability rules mirror backend `PermissionEvaluatorTest`, live-verified with viewer1/tester1).

- [x] **E2: User management page + role grants on `user_roles`**
  - Super/Org-admin page: list/create users, enable/disable, reset password, grant/revoke roles per
    (scope, project). Update the role-grant API to `user_roles`; protect last super-admin / last
    org-admin.
  - **AC:** admin manages users + grants; grantor needs ≥ that tier; last-admin protected.
  - Done: `UserAdminService`/`UserAdminController` (portal, `/api/portal/admin/users`) writing
    `user_roles`; only super/org-admin may manage; grantor-outranks-grant guard via `RoleResolver`;
    last-enabled-super-admin and last-org-admin protected; `User.resetPasswordByAdmin` forces
    change-on-next-login. Frontend `UsersPage` (create/enable-disable/reset/grant-revoke) + route +
    nav (gated MANAGE_ORG). **Verify:** `UserAdminServiceTest` (9, guards) green; portal 19/0;
    `tsc -b` green.

- [x] **CHECKPOINT E** — UI reflects roles (E1 nav/route gating); admins manage users + roles (E2).

---

## Phase F — Migration & cutover (DEPLOY — sign-off required)

- [x] **F1: `V6` migrate `team_members` → `user_roles`**
  - Map `ORG_ADMIN`→org `ORG_ADMIN`; `TEAM_ADMIN`→`PROJECT_ADMIN`(team's project); `TEAM_MEMBER`→
    `TESTER`; `VIEWER`→`VIEWER`. Create `users` rows for distinct `user_id`s **disabled +
    must_change_password** (no password-less login).
  - Done: F1a — `RoleResolver` + client `lib/auth.ts` honor org-scoped VIEWER/TESTER (the real data
    is 228 org-wide null-team grants). F1b — `V6` creates disabled/locked users + maps grants
    (team→PROJECT, org-wide→ORG of the single org). **Verified live:** V6 applied → 228 users (all
    disabled+must-change), 1 ORG_ADMIN·ORG + 227 VIEWER·ORG. `PermissionEvaluatorTest` +3.

- [x] **F2: Retire `X-Actor` trust; flip `platform.security.enabled=true`; `V7` drop `team_members`**
  - Derive actor from the verified principal everywhere; default-on enforcement; remove old table +
    `RbacService`/`team_members` paths.
  - Done: F2a — 16 `X-Actor` header params removed across 9 controllers; actor now from
    `CurrentUser.username()` (JWT). F2b — `platform.security.enabled` default flipped to true (code +
    compose). F2c — ADO bootstrap writes `user_roles` (disabled users); deleted `TeamMember`/repo,
    `RbacService`, ingestion `RoleController`/`RoleService`, portal `PortalRoleController`, frontend
    `RolesPage`; `V7` drops `team_members`. **Verified:** full reactor green; 6 images built;
    **deployed** — V6+V7 applied (team_members dropped, 231 users incl. 228 migrated); enforcement
    smoke 6/6 (default-on); browser: portal loads, tester nav = Agents/Task Agents only (no Roles),
    ForbiddenToast fires.

- [x] **CHECKPOINT F** — enforcement on by default; old model removed; deployed and verified.

---

## Resolved decisions (from spec §16)
- Auth: built-in users + BCrypt + httpOnly JWT cookie; bootstrap super-admin (`admin`/`PLATFORM_CRED_KEY`).
- Roles: project-scoped Org/Project Admin, Tester, Viewer (+ super flag); per-assigned-project.
- Enforcement: every service validates the token + capability; `X-Actor` retired.
- AI-gateway settings **and** ADO structure import → **Super Admin only**.
- 8h sliding token; admin-created users; app-logs-only audit.
