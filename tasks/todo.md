# TODO — ADO bootstrap integration

Vertical slices. Check off only when **Acceptance** and **Verify** both pass. Stop at each
**CHECKPOINT** for human review. Spec: `spec/ado-bootstrap.md`. Assumptions: see `tasks/plan.md` §2.

---

## Phase 1 — Org + credential from a PAT

### [x] B1 — Bootstrap endpoint: blank platform + PAT → platform Org + ORG-scoped credential
> DONE. `AdoBootstrapService.bootstrapOrg(pat, adoAccount, displayName)` upserts the Organization (by
> slug) + saves an ORG-scoped AZURE_DEVOPS_BOARDS credential (encrypted PAT, connectionParams.organization).
> `AdoOnboardingController` POST /api/v1/ado/onboard/org. Idempotent (CredentialService.save upserts by
> scope+type; org reused by slug). Tests 4/4.
- **Backend (platform-ingestion):** `AdoBootstrapService.bootstrapOrg(pat, adoAccountName, displayName)` →
  upsert `Organization` (name/slug from account) → save `AZURE_DEVOPS_BOARDS` credential at **ORG scope**
  (encrypted PAT, `connectionParams.organization = adoAccountName`). New `AdoOnboardingController`
  (`POST /api/v1/ado/onboard/org`). Idempotent: existing org by slug → reuse.
- **Acceptance**
  - From 0 orgs, the call creates exactly one Organization + one ORG-scoped credential; re-run reuses both.
  - `GET /credentials/{id}/azure/orgs` works on the created credential (discovery reused).
  - PAT stored encrypted; never returned.
- **Verify:** `mvn -q -pl platform-ingestion -am test`; unit test for `bootstrapOrg` (mock repos, assert org+cred upsert + idempotency).

> ### ✅ CHECKPOINT CP-A
> Blank platform + a real ADO PAT → Org + credential created; discovery lists that account. **Sign off.**

---

## Phase 2 — Projects

### [x] B2 — List ADO projects + seed platform Projects + integration configs
> DONE. `AzureOrgService.listProjects(credentialId)` (GET {account}/_apis/projects). `AdoBootstrapService.seedProjects`
> upserts a Project per ADO project (by org+slug) + an AZURE_DEVOPS_BOARDS ProjectIntegrationConfig
> (param.project, INBOUND). Onboard endpoint now chains org→projects. Idempotent. Tests 6/6 (+azure 2/2).
- **Backend:** extend `AzureOrgService` with `listProjects(credentialId, adoAccount)` (`{orgUrl}/_apis/projects`).
  `AdoBootstrapService` upserts a `Project` per ADO project under the Org + a `ProjectIntegrationConfig`
  (`integration_type=AZURE_DEVOPS_BOARDS`, `param.project=<adoProject>`, linked to the credential).
- **Acceptance**
  - Each ADO project becomes a platform Project (idempotent by org+ado-project) with an ADO config carrying `project`.
  - Re-run creates no duplicate projects/configs.
- **Verify:** `mvn -q -pl platform-ingestion -am test`; unit test for project/config upsert + idempotency (mock the projects list).

> ### ✅ CHECKPOINT CP-B
> Projects + configs seeded from a real ADO org; idempotent on re-run. **Sign off.**

---

## Phase 3 — Structure sync

### [x] B3 — Run structure sync + map ADO teams → platform Teams
> DONE. Moved `AzureOrgSyncService` + `AzureBoardsPollClient` from platform-agent → `platform-core`
> (`com.platform.core.service.ado`, scanned by CoreConfiguration so agent + ingestion both wire them);
> updated 3 agent references. `AdoBootstrapService.syncStructure(orgId)` runs `syncProject` per project
> (per-project failures collected, non-aborting) + upserts a platform Team per AdoTeam. Onboard endpoint
> chains org→projects→structure. Tests 8/8; platform-agent 95/95; full reactor builds.
- **Backend:** `AdoBootstrapService` calls `AzureOrgSyncService.syncProject` for each seeded project
  (populates `AdoTeam`/`AdoUser`), then upserts a platform `Team` per `AdoTeam`.
- **Acceptance**
  - After bootstrap, teams/areas/iterations/users are populated and platform Teams exist per ADO team; idempotent.
  - A single project's sync failure is reported per-item and doesn't abort the others.
- **Verify:** `mvn -q -pl platform-ingestion,platform-agent -am test`; unit test for the team-mapping + per-item result aggregation.

> ### ✅ CHECKPOINT CP-C
> Structure synced and Teams mapped against a real ADO org. **Sign off.**

---

## Phase 4 — Users + RBAC  (HIGHEST RISK — identity)

### [x] B4 — Provision platform Users + default role grants from ADO members
> DONE. `AdoBootstrapService.provisionMembers(orgId, credId)` collects ADO member identities (email else
> uniqueName) across all org projects, resolves the PAT owner via `AzureOrgService.resolveOwnerEmail`
> (`/_apis/profiles/me` → emailAddress), and grants org-wide `team_members` roles: each member → VIEWER,
> PAT owner → ORG_ADMIN. There is no User entity — roles attach to the id string. Strictly additive &
> idempotent: a member with any existing org-wide grant is left untouched (never downgraded); owner gains
> ORG_ADMIN only if missing; owner gets a grant even if not a project member. grantedBy="ado-bootstrap".
> Onboard endpoint chains org→projects→structure→members. Tests 11/11 (module 93/93).
- **Backend:** from `AdoUser`, upsert platform `User` (by ADO unique name/email) + a **VIEWER** grant via
  `RbacService`; grant the **PAT owner ORG_ADMIN** (resolve via `/_apis/connectionData` authenticated user).
  Strictly idempotent; **never** downgrade/clobber an existing user or grant.
- **Acceptance**
  - Users provisioned with least-privilege; PAT owner is ORG_ADMIN; re-run creates no dups and changes nothing existing.
  - No elevated roles granted beyond the single PAT-owner admin.
- **Verify:** `mvn -q -pl platform-ingestion,platform-core -am test`; unit tests: provision creates VIEWER + owner ORG_ADMIN; re-run no-op; existing admin not downgraded.

> ### ✅ CHECKPOINT CP-D (CRITICAL — identity)
> **Confirm the role policy** (VIEWER default + PAT-owner ORG_ADMIN) and verify no existing user/grant is
> modified, before this is run against real members. **Explicit sign-off required.**

---

## Phase 5 — Onboarding UI

### [x] B5 — First-run "Connect Azure DevOps" wizard
> DONE. `AdoOnboardWizard.tsx` (3 steps: enter PAT → discover accounts → pick org + bootstrap → summary +
> "Go to organization"). Wired into `OrgSelectPage` (featured empty-state CTA when 0 orgs + an action
> button alongside "New Organization"); on done invalidates orgs query and navigates to the seeded org.
> PAT is password-masked, sent only to the backend, never rendered back; errors surfaced via `postMsg`.
> New raw-PAT discovery: `AzureOrgService.discoverAccounts(pat)` + `POST /api/v1/ado/onboard/discover`
> (no credential persisted) + portal BFF `PortalAdoOnboardController` proxying discover + org. Backend
> tests 94/94 (AzureOrgServiceTest +discoverAccountsRejectsBlankPat); frontend tsc + vite build clean.
- **Frontend (platform-portal):** when 0 orgs exist, show a wizard: enter PAT → discover orgs (reuse) →
  select org(s) → confirm → call bootstrap → land in the seeded org. Add an "Onboard ADO org" action for later.
- **Acceptance**
  - From an empty platform, the wizard runs the full bootstrap and the user ends up in a seeded, usable org.
  - Loading/error/empty states use the shared components; secrets never rendered back.
- **Verify:** `cd platform-portal/frontend && npm run format && npx tsc -b && npx vite build`; manual run from an empty platform.

> ### ✅ CHECKPOINT CP-E
> End-to-end onboard from a blank platform in the browser. **Sign off to finish.**

---

## Cross-cutting acceptance (all tasks)
- Idempotent upserts keyed by ADO ids. · Encrypted PAT; no secrets in logs/GET/UI.
- Additive only (no deletion of platform entities for vanished ADO items). · Format + tests green before "done".
