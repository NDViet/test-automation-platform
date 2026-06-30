# Plan — Platform-wide Authentication & RBAC

Source: `spec/platform-rbac-spec.md`. Slices are **vertical** (schema → security core → endpoint →
enforcement → UI per capability) and dependency-ordered. TDD-first; one commit per task; a
`platform.security.enabled` flag (default **false**) keeps the platform usable until cutover.

> Note: the agent-management feature's tracking moved to `tasks/archive/agent-management-*.md`
> (it has a pending deploy + a deferred failure-analysis item). This plan is a separate, larger
> security feature — best done on its own branch.

## Ground truth from code exploration

- **No auth today.** `platform.actor` is typed on the Roles page → sent as `X-Actor`; backend
  services trust it. No `users` table, login, or session.
- **Existing RBAC is team-based + advisory.** `RbacService`/`team_members(user_id VARCHAR, team_id,
  role CHECK in ORG_ADMIN/TEAM_ADMIN/TEAM_MEMBER/VIEWER)`; enforced only at a few points.
- **Spring Security exists only in `platform-ingestion`** (`SecurityConfig` + `ApiKeyAuthFilter`,
  `X-API-Key`, gated by `platform.security.api-key.enabled`). The new JWT auth must **coexist** with
  that service-key chain (service-to-service) while adding **user** auth.
- **13 modules** in the root pom; add **`platform-security`** (depends on platform-core) that every
  service imports. Next Flyway version is **V5**.
- **`PLATFORM_CRED_KEY`** (env) is the bootstrap super-admin password; **`PLATFORM_JWT_SECRET`** (new
  env) signs/validates the HS256 token in every service.
- The portal is the front door; it must **forward the user JWT** (`Authorization: Bearer`) to
  services, which validate it independently — so a directly-reached service still 401/403s.

## Component dependency graph

```
        ┌─────────────────────────────────────────────┐
        │ A. Security core                            │  V5 users/user_roles + domain;
        │  platform-security: JwtService, Capability/ │  bootstrap super-admin;
        │  Tier, RoleResolver, PermissionEvaluator,   │  JwtAuthFilter + @RequireCapability
        │  SecurityFilterChain (flag-gated)           │  ← PURE + unit-tested
        └───────────────┬─────────────────────────────┘
                        │
        ┌───────────────▼───────────┐   ┌───────────────────────────────┐
        │ B. Auth endpoints + login │   │ (A enables both B and C)      │
        │ /auth/* + portal login UI │   └───────────────────────────────┘
        └───────────────┬───────────┘
                        │
        ┌───────────────▼───────────────────────┐
        │ C. Enforce in portal + agent (E2E)     │  annotate endpoints; fold AgentRbacGuard;
        │  portal forwards JWT; direct-call 401  │  verify bypass blocked
        └───────────────┬───────────────────────┘
                        │
        ┌───────────────▼───────────────┐
        │ D. Roll to ai/ingestion/       │  per-service filter + capability annotations
        │    analytics/integration       │  (AI-gateway & ADO-import → SUPER)
        └───────────────┬───────────────┘
                        │
        ┌───────────────▼───────────────┐   ┌──────────────────────────────┐
        │ E. Role-gated UI + user admin  │   │ F. Migration & cutover        │
        │  useAuth, <Can>, Users page    │   │  team_members→user_roles;     │
        └────────────────────────────────┘   │  retire X-Actor; flag on; drop│
                                              └──────────────────────────────┘
```

## Vertical slicing rationale

- **Phase A** is the only foundation block — the security primitives, proven by unit tests before
  any endpoint is gated. Pure (tier math, JWT), so fully testable without a running stack.
- **B** and **C** both depend only on A. B (login) is independently demoable; C is the first
  *enforced* vertical (login → portal → agent → role check) and proves the anti-bypass property.
- **D** is mechanical repetition of C's pattern across services (different capability mappings).
- **E** is UX (defense-in-depth, not the boundary) + the admin surface to actually manage users.
- **F** is the irreversible cutover: data migration + flipping enforcement on by default + dropping
  the old table — gated behind explicit sign-off (deploy).

## Checkpoints (human-verify gates)

- **CHECKPOINT A** — `platform-security` builds; bootstrap + evaluator + JWT unit tests green; no
  service gated yet.
- **CHECKPOINT B** — log in via the portal; bootstrap super-admin forced to change password; `/me`
  returns roles. (Flag still off elsewhere.)
- **CHECKPOINT C** — login→portal→agent enforced behind the flag; a direct call to platform-agent
  without a token is 401, with an under-privileged token is 403; Tester can CRUD agents, Viewer
  can't.
- **CHECKPOINT D** — every service enforces; cross-service matrix test passes (AI-gateway/ADO-import
  require SUPER).
- **CHECKPOINT E** — UI hides/disables by role; super/org-admin can manage users + role grants.
- **CHECKPOINT F** — `team_members` migrated + dropped; `X-Actor` retired; `platform.security.enabled`
  default true; full regression green; deployed.

## Risks / watch-items

- **Lockout** is the dominant risk — the flag stays **false** until C/D are verified; bootstrap must
  fail-fast (never empty password); the last super-admin / org's last org-admin are protected.
- **Coexistence with ingestion's API-key chain** — JWT (user) and X-API-Key (service) must both
  work; don't break portal→ingestion service calls.
- **Token vs revocation** — roles resolved per request (not from the token), so a tiny per-request
  DB read; cache later if hot.
- **Every endpoint must be classified** — default-deny means an unmapped endpoint blocks; the matrix
  test must cover representative endpoints per service, and unmapped → explicit decision.
- **F is a deploy** (migration + flag flip + image rebuild) — stop for sign-off.
