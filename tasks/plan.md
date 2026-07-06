# PLAN — Provision ADO team members as platform users

Spec: `SPEC.md`. Auth/identity feature; default credential model = **provision enabled but login-locked**
(secure random unknown password + mustChangePassword; admin resets to enable login). No schema change
(link by email). Build order = dependency order. Each task: RED → GREEN → regression → build → 1 commit.

## Task 1 — Backend: AdoProvisioningService (link-or-create + grant)  ⟶ 1 commit
Add `Optional<User> findByEmailIgnoreCase(String email)` to `UserRepository` (core, the link key).
New `portal/admin/AdoProvisioningService.provisionProject(UUID projectId, AuthenticatedUser actor)`:
admin-only (super or ORG_ADMIN — mirror `UserAdminService.requireUserAdmin`); load
`AdoUserRepository.findByProjectIdOrderByDisplayName`, keep `isTeamMember()` with a non-blank email;
per member — find `users.findByEmailIgnoreCase(email)`: absent → create `User(username, email,
encoder.encode(<secure-random>), displayName, superAdmin=false, mustChangePassword=true)` **enabled**
(so it's assignable) and login-locked (unknown password), username derived from email local-part /
unique_name and de-duplicated on `existsByUsername`; present → link. Then ensure a PROJECT **TESTER**
`UserRole` (idempotent via `findByUserIdAndRoleAndScopeAndScopeId`). Return a `ProvisionSummary`
{total, created, linked, granted, alreadyMembers, skippedNoEmail}. Secure random password never logged.
- RED: creates enabled+must-change users with an unknown password and a PROJECT TESTER grant for team
  members with email; idempotent (existing user by email → linked, existing grant → not re-saved);
  skips no-email members; non-admin actor → 403.

## Task 2 — Backend: admin endpoint + audit  ⟶ 1 commit
New `AdoProvisioningController`: `POST /api/portal/admin/projects/{projectId}/provision-ado-users` →
`service.provisionProject(projectId, CurrentUser.get())` → `ProvisionSummary`. Audited via the existing
audit mechanism if available (else structured log noting actor + counts; never log credentials).
- RED: controller delegates to the service with the JWT principal and returns the summary (thin
  delegate test); wiring compiles.

## Task 3 — Frontend: "Provision team from ADO" admin action  ⟶ 1 commit
`api.provisionAdoUsers(projectId)` + `ProvisionSummary` type. In the project admin/team area, a
"Provision team from ADO" button with a confirm dialog (bulk action) that shows a preview (ADO team
members vs already-provisioned) and, on success, the summary (created/linked/granted/skipped). Design
tokens + `ui/` kit.
- Verify: `tsc -b` clean.
