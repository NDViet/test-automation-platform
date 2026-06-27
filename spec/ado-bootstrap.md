# SPEC — ADO as the bootstrap integration

> Status: DRAFT — awaiting confirmation, then `/plan` → `/build`.
> Scope: platform-ingestion, platform-agent, platform-core, platform-portal.

## 1. Objective
A blank platform (no Organization) can onboard entirely from Azure DevOps: add an ADO PAT, pick the
ADO org(s), and the platform **seeds itself** — Organization → Projects (+ integration configs) →
ADO structure (teams/areas/iterations/users) → platform Teams and Users/RBAC. Removes the manual
"create an Organization first" chicken-and-egg.

## 2. Decisions (confirmed)
| Topic | Decision |
|---|---|
| Bootstrap depth | **Org + Projects + structure** — create the Organization, all ADO Projects + their `ProjectIntegrationConfig`, then run the existing teams/areas/iterations/users sync. |
| Users / RBAC | **Provision platform Users + default role grants** from ADO members (identity-sensitive — see boundaries). |
| Credential model | ADO credential can be added **org-less** (a bootstrap path), since the Org doesn't exist yet. |
| Idempotency | Re-onboarding upserts by stable keys (ADO account/project/team/user ids); never duplicates. |

## 3. Mapping (ADO → platform)
| Azure DevOps | Platform |
|---|---|
| Organization (account) | `Organization` (name/slug from accountName) |
| Project | `Project` under the Org + `AZURE_DEVOPS_BOARDS` `ProjectIntegrationConfig` (organization+project params + credential) |
| Team | `AdoTeam` (directory) → platform `Team` |
| Member | `AdoUser` (directory) → platform `User` + default role grant |

## 4. What to reuse vs build
**Reuse:** `azure/orgs` discovery (done); `AzureOrgSyncService.syncProject` (teams/areas/iterations/users);
`OrganizationManagementService.create`, project creation, `TeamManagementService`; `AzureManagedOrg` table.

**Build:**
- **Org-less ADO credential**: allow saving/`test`/`azure/orgs` for an `AZURE_DEVOPS_BOARDS` credential
  with no platform scope (a `BOOTSTRAP`/global scope, or a dedicated onboarding endpoint holding the PAT).
- **Azure projects API**: list projects in an ADO org (`{org}/_apis/projects`) via the PAT (extends `AzureOrgService`/client).
- **`AdoBootstrapService`** (platform-ingestion): for each selected ADO org → upsert `Organization` →
  list projects → upsert `Project` + `ProjectIntegrationConfig` → call structure sync → map `AdoTeam`→`Team`,
  `AdoUser`→`User` + RBAC. Fully idempotent; returns a summary (orgs/projects/teams/users created).
- **Onboarding endpoint(s)** + **frontend onboarding flow**: from a blank platform, “Connect Azure DevOps”
  → enter PAT → pick orgs → confirm → bootstrap runs → land in the seeded org.

## 5. Acceptance criteria
- **AC1** From an empty platform (0 orgs), entering a valid ADO PAT and selecting an ADO org creates a
  platform Organization, its Projects, integration configs, and synced structure — no manual Org step.
- **AC2** Re-running the bootstrap for the same ADO org makes no duplicates (upsert by ADO ids).
- **AC3** Platform Users are provisioned from ADO members with a **non-admin default role**; existing
  users/grants are not downgraded or clobbered.
- **AC4** A bootstrap that partially fails (e.g. one project's sync) reports per-item status and leaves a
  consistent state (no half-created Org without a recorded credential link).
- **AC5** The org-less ADO credential is scoped/owned correctly once its Organization exists (re-parented to the new Org).

## 6. Boundaries
**Always**
- Idempotent upserts keyed by ADO ids. Reuse `AzureOrgSyncService`; don't duplicate structure logic.
- Encrypted PAT handling; never log/return secrets.
- Per-item bootstrap result so partial failures are visible.

**Ask first**
- The exact default **role** granted to provisioned users, and whether the PAT owner becomes ORG_ADMIN.
- Whether the org-less credential is a new `BOOTSTRAP` scope vs a transient onboarding token.

**Never (without explicit sign-off)**
- Auto-grant ORG_ADMIN / elevated roles broadly from ADO membership.
- Overwrite or downgrade existing platform users, roles, orgs, or projects.
- Delete platform entities for ADO items that disappeared (sync is additive/upsert; deletion is out of scope).

## 7. Risks
- **Identity/RBAC (high):** provisioning users + grants touches auth and is hard to fully `git revert`
  once accounts exist. Land it behind the role decision (Ask-first) and default to least privilege.
- **ADO APIs unverified without a real PAT** (projects/profile/accounts) — same caveat as org discovery.
- **Scope re-parenting:** moving the bootstrap credential under the new Org must not break resolution.

## 8. Out of scope
Deletion/de-provisioning; SSO/login wiring for provisioned users; non-ADO bootstrap (GitHub/Jira).

## 9. Open items to confirm
1. Default role for provisioned users; ORG_ADMIN for the PAT owner? (drives AC3)
2. Org-less credential: new `BOOTSTRAP` scope vs transient onboarding token? (drives AC5)
3. Bootstrap trigger: a one-shot onboarding wizard, or reuse `/settings/integrations` + an "Onboard org" action?
