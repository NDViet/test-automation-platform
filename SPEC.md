# SPEC — Fix: Impact Analyses ignores "Codebase" repos set via the GitHub tab

> Status: **DRAFT — awaiting confirmation.** Bug fix. Prior spec (Portal UI design system, complete)
> archived at `spec/archive/portal-ui-design-system-spec.md`.

## 1. Objective

On the Impact Analyses page (`/{org}/{project}/impact-analyses` → "New Impact Analysis" → step 1),
a repository that has been marked **Codebase** in **Project Settings → GitHub** still shows:

> **No Codebase repos configured** — Go to Project Settings and mark a GitHub repo as Codebase to
> list PRs here.

### Root cause (confirmed by code trace)
There are **two independent surfaces** for tagging a repo "Codebase", persisted to **two different
tables**, and the PR-listing endpoint reads only one of them:

| Surface (Project Settings) | Persists to | Carries |
|---|---|---|
| **Integrations** tab → GITHUB config, Repo Role = Codebase | `ProjectIntegrationConfig` (`integration_config`) | `repoFullName`, `token` params, `repoType=CODEBASE` |
| **GitHub** tab → repo picker, role = Codebase | `ProjectRepoAssignment` (`project_repo_assignments`) | `repoFullName`, `role=CODEBASE`, `credentialId` (→ org PAT) |

`ImpactAnalysisController.listOpenPrs` (platform-agent, `GET /hub/impact/{projectId}/prs`) queries
**only** `ProjectIntegrationConfigRepository.findByProjectIdAndIntegrationTypeAndRepoTypeAndEnabled(
projectId, "GITHUB", "CODEBASE", true)`. A repo marked Codebase via the **GitHub tab** writes
`ProjectRepoAssignment` and is therefore never seen → empty list → the misleading message.

**User confirmed:** the repo was configured via **Project Settings → GitHub** tab.

### Target users
- Portal users (QA leads/engineers) creating an Impact Analysis — must see PRs from any repo they
  marked Codebase, regardless of which settings surface they used.

## 2. Resolved decision (locked)
**Read both sources (chosen).** `listOpenPrs` treats a repo as Codebase if it is marked so in
**either** `ProjectIntegrationConfig` (existing path) **or** `ProjectRepoAssignment` with
`role='CODEBASE'` (new path). For assignment-sourced repos the GitHub token is resolved from the
referenced credential via the existing **Org→Project→Team credential cascade**
(`CredentialResolver` / `IntegrationTokenResolver`, already used by `GitHubPollingService`).
Results are **merged and de-duplicated by `repoFullName`** so a repo present in both surfaces is
listed once.

Not chosen: write-through sync GitHub-tab→IntegrationConfig; UI consolidation. (Deferred — see §10.)

## 3. Acceptance criteria
- [ ] With a repo marked **Codebase in the GitHub tab only** (a `ProjectRepoAssignment`, `role=CODEBASE`,
      no `integration_config` CODEBASE row), `GET /hub/impact/{projectId}/prs` returns that repo's open
      PRs, and the Impact Analyses "New" modal lists them (no "No Codebase repos configured" message).
- [ ] With a repo marked Codebase via the **Integrations tab only** — unchanged: still works exactly
      as today (no regression).
- [ ] A repo marked Codebase in **both** surfaces appears **once** (dedupe by `repoFullName`).
- [ ] Assignment-sourced repos fetch PRs using the token resolved from their credential; if no token
      resolves, that repo is skipped with a `log.warn` (never a 500) — consistent with the existing
      per-repo `try/catch`.
- [ ] Each returned PR keeps the same JSON shape (`number, title, html_url, state, user, updated_at,
      head_ref, base_ref, body(≤500 chars), repoFullName`) so `CodabasePr` / the frontend need no change.
- [ ] Empty result (genuinely no Codebase repo in either surface) still shows the existing empty state.
- [ ] Backend build + tests green; no frontend behavior/logic change required (the empty-state copy may
      optionally be broadened to mention both tabs — see §7).

## 4. Approach (implementation sketch — for the plan, not binding)
In `platform-agent/.../api/ImpactAnalysisController.java` (`listOpenPrs`):
1. Keep the existing `ProjectIntegrationConfig` CODEBASE loop.
2. Add a second source: inject `ProjectRepoAssignmentRepository` (platform-core) +
   `CredentialResolver`/`IntegrationTokenResolver` (already in platform-agent). Load
   `findByProjectIdOrderByRepoFullName(projectId)`, filter `role == "CODEBASE"`.
3. For each assignment, resolve the GitHub token via the credential cascade (mirror
   `GitHubPollingService.pollProject`), call `gitHubApiClient.getOpenPRs(owner, repo, token)`, map PRs
   to the same entry shape.
4. Merge both lists into one `result`, de-duplicating repos by `repoFullName` (prefer whichever
   resolves a usable token; a repo already fetched from IntegrationConfig is not re-fetched).
5. Preserve per-repo `try/catch` + `log.warn`; overall endpoint returns `200` with the merged list.

Extract a small private helper (e.g. `fetchOpenPrsForRepo(owner, repo, token, repoFullName, result)`)
to avoid duplicating the PR→map projection.

## 5. Commands
- **Build/test (backend):** `mvn -q -pl platform-agent -am test` (and `-pl platform-core` if the repo
  gains a query method). Full: `mvn -q verify` for the affected modules.
- **Frontend (only if copy is touched):** `cd platform-portal/frontend && npx tsc -b`.
- **Manual verify:** with a project whose repo is Codebase via the GitHub tab, open the Impact
  Analyses "New" modal and confirm PRs list.

## 6. Project structure (touched)
```
platform-agent/src/main/java/com/platform/agent/api/ImpactAnalysisController.java   # fix site (listOpenPrs)
platform-agent/src/main/java/com/platform/agent/node/tools/IntegrationTokenResolver.java  # reuse (token)
platform-agent/src/main/java/com/platform/agent/hub/polling/GitHubPollingService.java      # pattern to mirror
platform-core/src/main/java/com/platform/core/repository/ProjectRepoAssignmentRepository.java  # source #2 (maybe + findByProjectIdAndRole)
platform-core/src/main/java/com/platform/core/domain/ProjectRepoAssignment.java
# unchanged unless copy tweaked:
platform-portal/frontend/src/pages/ImpactAnalysesPage.tsx   # empty-state message (optional wording)
platform-portal/frontend/src/lib/api.ts                     # codebasePrs — no change
platform-portal/src/main/java/com/platform/portal/api/PortalImpactController.java  # proxy — no change
```

## 7. Code style / conventions
- Java: constructor injection (match existing controller); keep the per-repo `try/catch` + `log.warn`;
  no new endpoint or response-shape change; reuse `CredentialResolver`/`IntegrationTokenResolver` and
  `GitHubApiClient` rather than adding a new GitHub client.
- Prefer a repository query method (`findByProjectIdAndRole(projectId, "CODEBASE")`) over in-memory
  filtering if adding one is cheap and consistent with the repo's style.
- Optional FE copy tweak: broaden the empty-state hint to "…mark a GitHub repo as Codebase (Project
  Settings → GitHub, or Integrations)". Presentation-only; keep the migrated design tokens.

## 8. Testing strategy
- **Unit/slice (backend):** cover `listOpenPrs` for three cases — assignment-only, config-only, both
  (dedupe) — with `GitHubApiClient` stubbed to return canned PR JSON and `CredentialResolver` stubbed
  to yield / withhold a token. Assert merged/deduped output and that a token-less repo is skipped, not
  fatal. Follow the module's existing test framework (JUnit + Mockito as used elsewhere in platform-agent).
- **Regression:** existing IntegrationConfig-only behavior unchanged.
- **Manual:** the §5 walkthrough against the reported project.

## 9. Boundaries
**Always**
- Preserve the existing IntegrationConfig path and the endpoint's JSON contract (`CodabasePr`).
- Resolve tokens only through the existing credential cascade; keep secrets out of logs/responses.
- Per-repo failures degrade gracefully (skip + warn), never 500 the whole list.

**Ask first**
- Adding a DB migration or new table/column (none expected — reuses existing tables).
- Changing the `/prs` response shape, the `CodabasePr` type, or the create-analysis flow.
- Any write-through sync between the two config surfaces, or UI consolidation (§10).
- Touching credential decryption internals or `CredentialResolver` behavior.

**Never**
- Log or return raw PATs/tokens.
- Duplicate a repo's PRs when it's configured in both surfaces.
- Broaden scope into unrelated impact-analysis logic (AI run, suggestions) while fixing PR listing.

## 10. Out of scope (deferred)
- Reconciling/merging the two "Codebase" surfaces into one source of truth (write-through sync or UI
  consolidation) — larger UX/data decision; revisit separately.
- Test-Automation-repo parity (same two-surface split may exist for `TEST_AUTOMATION`; not in this fix
  unless the plan finds it trivially adjacent).
- Any change to how repos are marked Codebase in the UI.
