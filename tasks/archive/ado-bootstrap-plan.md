# PLAN — ADO as the bootstrap integration

From `spec/ado-bootstrap.md`. Read-only analysis done; no code changed. Vertical slices + checkpoints.
(The completed LiteLLM plan is archived under `tasks/archive/`.)

## 1. Grounding facts (from codebase)
- **`CredentialResolver`** cascades **ORG→PROJECT→TEAM**; ORG is keyed by the project's organization id.
  → Bootstrap can create the Org, save the PAT as an **ORG-scoped** `AZURE_DEVOPS_BOARDS` credential
  (`connectionParams.organization` = ADO account), and every seeded project resolves it automatically.
  **No new credential scope and no re-parenting needed.**
- **`AzureBoardsPollClient.connect(projectId)`** reads `cred.param("organization")` + the project's
  config `param("project")` → so bootstrap must set those.
- **`AzureOrgSyncService.syncProject(projectId)`** already syncs teams/areas/iterations/users into
  `AdoTeam`/`AdoUser` for a project that has an ADO config. **Reuse as-is.**
- **`OrganizationManagementService.create` / project creation / `TeamManagementService` / `RbacService`**
  exist for entity creation + grants.
- **`AzureOrgService`** (just added) does org discovery; extend it with a projects listing.
- Org discovery + accounts/profile APIs are **unverified without a real ADO PAT**.

## 2. Working assumptions (open items not yet answered — confirm at review)
1. **Default role** for provisioned users = **VIEWER**; the **PAT owner → ORG_ADMIN** (so the seeded org
   has exactly one admin). Surfaced again at CP-D.
2. **Org-less onboarding** = a bootstrap endpoint that accepts the PAT inline, creates the Org, then
   persists the credential **ORG-scoped** under the new org. (No `BOOTSTRAP` scope enum.)
3. **Trigger** = a first-run "Connect Azure DevOps" wizard shown when 0 orgs exist, plus an
   "Onboard ADO org" action for adding more later.

## 3. Component dependency graph
```
AzureOrgService.listProjects (ADO projects API)         ─┐
Organization/Project/Config creation (exists) ──────────┤
                                                         ▼
B1 Org + ORG-scoped credential from PAT  ──►  B2 Projects + ADO configs  ──►  B3 Structure sync (+Team map)
                                                                                      │
                                                                                      ▼
                                                                            B4 Users + RBAC  (identity)
                                                         ┌───────────────────────────┘
                                                         ▼
                                              B5 Onboarding wizard (UI)  — consumes B1–B4
```
Build order **B1 → B2 → B3 → B4 → B5**. B5's UI can be stubbed against each endpoint as it lands, but the
full wizard comes after B4.

## 4. Why vertical
Each slice is a complete path: B1 "blank → Org + credential", B2 "→ projects", B3 "→ synced structure",
B4 "→ users", B5 "→ do it all from the browser". No half-built 'service exists but nothing calls it' state.

## 5. Phases & checkpoints
| Phase | Tasks | Checkpoint (human gate) |
|---|---|---|
| P1 Org + credential | B1 | **CP-A:** blank platform + PAT → platform Org created + ORG-scoped credential saved; `azure/orgs` works |
| P2 Projects | B2 | **CP-B:** ADO projects listed + platform Projects + configs created (idempotent) |
| P3 Structure | B3 | **CP-C:** teams/areas/iterations/users synced; AdoTeam→Team mapped |
| P4 Users + RBAC | B4 | **CP-D (critical, identity):** users provisioned least-privilege; PAT owner ORG_ADMIN; **no clobber/downgrade**; re-run no dup — sign off the role policy |
| P5 Onboarding UI | B5 | **CP-E:** end-to-end from an empty platform in the browser |

Stop at each checkpoint for review.

## 6. Risks & mitigations
- **R1 Identity/RBAC (high):** B4 creates accounts + grants — hard to `git revert` once real. Default
  least-privilege, idempotent upserts keyed by ADO user id, never downgrade existing; gate on CP-D sign-off.
- **R2 ADO APIs unverified:** projects/profile/accounts need a real PAT. Implement to spec; verify at
  checkpoints with a token (supply one to make CP-A..CP-D real).
- **R3 Idempotency:** every upsert keyed by ADO ids (account/project/team/user); re-onboard must not dup.
- **R4 Partial failure:** bootstrap returns per-item status; a failed project sync must not leave an
  Org without its recorded credential.

## 7. Out of scope (per spec)
Deletion/de-provisioning; SSO/login wiring; non-ADO bootstrap.

## 8. Definition of done
All AC (spec §5) met; `mvn -Pformat` clean; module tests green; frontend `tsc -b`+`vite build` green;
Trivy HIGH/CRITICAL = 0; bootstrap idempotent; identity changes least-privilege + non-destructive.
