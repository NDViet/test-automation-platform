# SPEC — Provision ADO team members as platform users (assignable + login-capable)

> Status: **DRAFT — awaiting confirmation.** Auth/identity feature; **explicit sign-off required before
> build** (creates login accounts + grants project roles at scale). Prior specs archived under
> `spec/archive/`. Follows on from the member-picker work (`spec/archive/manual-run-member-picker-spec.md`):
> the searchable picker is deployed but points at the platform `users` table, which for this project
> holds only 2 login users — the ~190 real people live in `ado_users`.

## 1. Objective

Make the **ADO-imported project team searchable and assignable** in the portal by **provisioning each
ADO team member as a platform `users` row with a PROJECT role**, so they (a) appear in the
`assignable-users` picker (Triggered By / Default Assignee / run Assignee) searchable by name + email,
and (b) can log in and work their **My Queue**. Assignment stays a FK to `users(id)` (no data-model
change to the run-assignee feature); we populate `users` from ADO instead.

### Target users
Project/org admins who onboard a project's ADO team into the portal; QA engineers who then assign/triage
manual runs to real teammates.

### Grounding (verified)
- **Populations:** `ado_users` (project-scoped: `unique_name`, `display_name`, `email`,
  `is_team_member`, `seen_on_work_items`, `quality_role`) has **190 rows (184 with email)** for
  hrs365/product-house. Platform `users` has 3 enabled (admin + tester1 + viewer1). Repos:
  `AdoUserRepository.findByProjectIdOrderByDisplayName`.
- **Assignment** FKs to `users(id)` (`test_runs.assignee_user_id`, executions); the picker
  (`ExecutionPlanningService.assignableUsers(projectId)`, just shipped) lists enabled users with a
  PROJECT-scoped `UserRole` for the project, carrying email. So a provisioned+granted user is
  immediately assignable/searchable.
- **Reuse:** `UserAdminService.create` (`new User(username,email,hash,displayName,super=false,
  mustChangePassword=true)`) + `grant` (PROJECT `UserRole`, roles PROJECT_ADMIN/TESTER/VIEWER);
  `requireUserAdmin` (super/org-admin only); `PasswordEncoder`; `resetPasswordByAdmin`.
- **Auth:** password + JWT cookie (`AuthController /login`), **no SSO/OIDC**. `assignableUsers` filters
  `User::isEnabled` — so provisioned users must be **enabled** to be assignable.

### Assumptions locked for this draft (clarifying Qs unanswered — confirm, esp. #3)
1. **Population = ADO team members with an email** (`is_team_member=true`, `email` present). Email is
   the natural link key. Members without email are reported/skipped (can't safely key or notify).
2. **Default role = TESTER** (OPERATE tier) so they can execute + work My Queue. (VIEWER can't.)
3. **Credentials (no SSO) = provision enabled but login-locked.** Create the user **enabled** (so it's
   assignable now) with a **random, unknown password** + `mustChangePassword=true`; the person cannot
   log in until an **admin resets their password** (existing `resetPassword`/invite flow), on demand.
   Rationale: satisfies "assignable now + can work the queue after invite" **without** minting ~190
   known/dormant credentials. ⚠️ This is the risky decision — alternatives: (a) generated temp password
   emailed per user, (b) real **SSO/OIDC** (the proper fit for corporate `@int.hrs.com` identities,
   recommended as a follow-up, out of scope here). Confirm which you want.
4. **Idempotent link-or-create, no deletions.** Re-running links existing `users` by email (ensures the
   PROJECT grant), creates missing ones, never duplicates, and **never disables/deletes** users who
   left ADO (a separate lifecycle concern, out of scope). `username` derived from email local-part /
   `unique_name`, de-duplicated on collision.

## 2. Scope

**In:**
1. **Provisioning service** (portal `admin` package, beside `UserAdminService`):
   `AdoProvisioningService.provisionProject(projectId, actor)` — load ADO team members (with email),
   for each: find `users` by email → if absent create (`enabled`, random password,
   `mustChangePassword`, displayName+email) → ensure a PROJECT `UserRole` (default TESTER) →
   collect a per-person outcome (CREATED / LINKED / GRANTED / SKIPPED_NO_EMAIL / ALREADY). Reuses the
   `UserAdminService` authz (`requireUserAdmin`) + grant rules. Idempotent; transactional.
2. **Admin endpoint** `POST /api/portal/admin/projects/{projectId}/provision-ado-users` (super/org-admin
   only) → summary `{created, linked, granted, skipped, total}`. Audited.
3. **UI** — an admin action (Team/Users admin area) "**Provision team from ADO**" for a project: shows a
   preview count (ADO team members vs already-provisioned) and a confirm button; on success shows the
   summary. Reuses existing admin UI patterns + design tokens.
4. **User linkage field** (optional, additive) — record the ADO source on the created user for
   idempotency/audit (e.g. `users.ado_unique_name` or reuse `email` as the sole key — see §Data).

**Out:** SSO/OIDC; auto-sync on ADO poll (this is an explicit admin action, not automatic);
de-provisioning/disabling leavers; changing the assignee data model (stays `users` FK); bulk password
distribution/email (login enabled per-person via existing reset/invite).

**Data:** prefer **no schema change** — link by `email` (already unique-ish; enforce case-insensitive
match). If a durable ADO link is wanted, an additive **V25** `ALTER TABLE users ADD COLUMN
ado_unique_name TEXT` (nullable) — decide during planning; default is email-only, no migration.

## 3. Acceptance criteria
- Running provisioning for the project creates/links a platform `users` row for each ADO team member
  with an email, each **enabled** with a **PROJECT TESTER** grant; they then appear in the
  `assignable-users` picker searchable by **name and email**.
- Idempotent: a second run creates nothing new, adds no duplicate grants, and reports ALREADY for
  existing members; members without email are reported SKIPPED, not created.
- A provisioned user **cannot log in** until an admin resets their password (no known/default
  credential is set); once reset, they log in and see assigned cases in My Queue.
- Endpoint is restricted to super/org-admins (reuses `requireUserAdmin`); the action is audited.
- Backend module tests green; `npx tsc -b` clean; design tokens; no secret/credential logged.

## 4. Commands
- **Backend:** `mvn -q -pl platform-portal -am test` (provisioning lives in portal `admin`).
- **Frontend:** `cd platform-portal/frontend && npx tsc -b`.
- **Deploy:** `deploy-local` — rebuild `platform-portal` (`-am`); **migration only if** the optional
  V25 link column is chosen; `up -d --no-deps`; verify.

## 5. Project structure (touched)
```
platform-portal (BE) # admin/AdoProvisioningService (+ Dtos), admin controller endpoint;
                     #   reuses UserAdminService authz + UserRepository/UserRoleRepository/PasswordEncoder,
                     #   AdoUserRepository (core)
platform-portal (FE) # admin Users/Team page: "Provision team from ADO" action + summary; api + types
(optional) platform-core # V25 users.ado_unique_name (only if a durable ADO link is chosen)
```

## 6. Code style / testing / boundaries
- **Java:** constructor injection; reuse `UserAdminService` authz + password encoder; generate the
  unknown password with a secure random (never a fixed/guessable value; never logged);
  `mustChangePassword=true`. JUnit5 + Mockito + AssertJ; strict stubs.
- **Frontend:** design tokens + `ui/` kit; confirm dialog before a bulk action; show the outcome
  summary; `tsc -b` gate.
- **Testing:** provisioning creates+grants only for team members with email; is idempotent (no dup
  users/grants on re-run); skips no-email; sets a non-usable password + mustChangePassword; enforces
  admin-only. Verify the picker then lists a provisioned user (integration-style with real repos where
  practical).
- **Boundaries — Always:** admin-only + audited; enabled+TESTER so they're assignable and can operate;
  idempotent; email as link key; secure random password. **Ask first (SIGN-OFF GATE):** the credential
  model (assumption #3) and whether to add SSO; any auto-sync; adding the V25 link column. **Never:**
  set a known/shared/guessable password; log passwords; disable/delete ADO leavers here; change the
  assignee FK; grant ORG-level or admin roles via this flow.
