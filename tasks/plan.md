# Plan — Fix Impact Analyses "No Codebase repos configured"

Spec: `SPEC.md`. Approach (locked): **read both sources** — union of `ProjectIntegrationConfig`
(repoType=CODEBASE) and `ProjectRepoAssignment` (role=CODEBASE), deduped by `repoFullName`;
assignment-sourced repos get their PAT from the `CredentialResolver` Org→Project→Team cascade.

Verification gate: `mvn -q -pl platform-agent -am test` (+ `platform-core` compiled transitively).
Reversible via `git revert`. Backend-only; no schema change; no response-shape change.

## Task 1 — Merge GitHub-tab CODEBASE repos into `listOpenPrs` (backend)  ⟶ 1 commit
**File:** `platform-agent/.../api/ImpactAnalysisController.java` (+ new test).
**Deps:** none.

RED — add `ImpactAnalysisControllerPrsTest` (JUnit5 + Mockito, mirroring `ImpactAnalysisServiceTest`)
constructing the controller with mocked `ProjectIntegrationConfigRepository`,
`ProjectRepoAssignmentRepository`, `CredentialResolver`, `GitHubApiClient`, `ObjectMapper`. Cases:
1. **assignment-only**: one `ProjectRepoAssignment(role=CODEBASE, repo "o/a")`, no CODEBASE config;
   `CredentialResolver.resolve(pid,"GITHUB")` → token; `getOpenPRs("o","a",token)` → 1 PR ⇒ result has
   that PR (currently **fails** — returns empty).
2. **config-only**: one CODEBASE `ProjectIntegrationConfig` (repoFullName "o/c", token) ⇒ PR listed
   (regression guard — already passes).
3. **both surfaces, same repo "o/x"**: appears **once** (dedupe by repoFullName).
4. **assignment with no resolvable token**: skipped, no exception, endpoint returns 200.

GREEN — inject `ProjectRepoAssignmentRepository` + `CredentialResolver`; after the existing config
loop, load `findByProjectIdOrderByRepoFullName(pid)`, filter `role=="CODEBASE"`, skip repos already
emitted (track a `Set<String> seen` of repoFullName from the config loop), resolve token via the
cascade (mirror `GitHubPollingService.inheritedToken`), fetch + map PRs with the **same** entry shape.
Extract private `fetchOpenPrs(owner, repo, token, repoFullName, result)` to share the PR→map
projection; keep per-repo `try/catch` + `log.warn`; never log the token.

Acceptance: SPEC §3 bullets 1–5. Regression: `mvn -q -pl platform-agent -am test` green.

## Task 2 — Broaden empty-state hint copy (frontend, optional)  ⟶ 1 commit
**File:** `platform-portal/frontend/src/pages/ImpactAnalysesPage.tsx` (CreateModal empty state).
**Deps:** Task 1 (so copy matches new behavior).

Change the hint to point at **both** surfaces: "…mark a GitHub repo as **Codebase** — in Project
Settings → **GitHub** (repo roles) or **Integrations**." Presentation-only, keep design tokens.
Gate: `cd platform-portal/frontend && npx tsc -b`. (No unit runner for the SPA.)

## Out of scope (SPEC §10)
Write-through sync / UI consolidation of the two surfaces; TEST_AUTOMATION parity.
