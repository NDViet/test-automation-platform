# SPEC — Enhance Impact Analyses: editable jobs, richer context, PR filter/search

> Status: **DRAFT — awaiting confirmation.** Builds on the deployed Impact Analyses fixes (PR listing
> from GitHub-tab Codebase repos, PR-diff token via credential cascade). Prior spec archived at
> `spec/archive/impact-analyses-codebase-fix-spec.md`.

## 1. Objective

Make an Impact Analysis a **living, refinable job** instead of a one-shot create-and-forget. A user can
**edit an existing analysis** — add PRs (across repos), add requirements, and add free-text
**instructions for the AI** — then **re-run** it; the agent recomputes the result with the fuller
context. The PR picker gains a **repo filter** and **PR search (title / number)**, and the agent feeds
the AI more signal: each PR's **description** and the **full diff of changed Markdown files** (which
are often specs authored by a coding agent), while non-Markdown files stay as a changed-file list.

### Target users
- **QA leads / engineers** iterating on an impact analysis as PRs land or scope grows — refine and
  re-run without recreating the job.

### Locked decisions (from clarification)
1. **Re-run fresh, replace result.** Editing + re-running recomputes the whole analysis from the full
   updated context; the new `summary` + `suggestions` **replace** the previous ones. No versioning.
2. **Single editable instructions field** — one free-text `customPrompt` per job (new column), fed to
   the AI on every run; editable each time.
3. **Diff depth:** keep the **changed-file list** for all files; additionally include each PR's
   **description/body**, and for changed files ending in `.md`/`.markdown`, include their **diff
   (patch)** content (potential coding-agent specs). No full patch for non-Markdown files. All added
   context is **size-bounded** to protect token budget.

## 2. Scope

### In scope
- **Edit + re-run endpoint** (agent + portal proxy) to update a job's `linkedPrs`,
  `linkedRequirementIds`, `name`, and `customPrompt`, then re-run asynchronously (fresh, replaces
  result). Blocked while a run is in progress.
- **New field** `custom_prompt` on `impact_analyses` (Flyway `V10`).
- **Richer context assembly** in `ImpactAnalysisService`: PR descriptions + Markdown-file diffs +
  existing changed-file list + `customPrompt`, all bounded.
- **Frontend**: PR-picker **repo filter dropdown** + **search (title/number)**; **Edit** action that
  reopens a job pre-filled (PRs, requirements, instructions), supports adding more, and Saves → re-run;
  an **"Instructions for the AI"** textarea in create + edit.

### Out of scope (deferred)
- Result versioning / run history; augment-merge of prior suggestions (decision #1 = replace).
- Full patch content for non-Markdown files; binary/large-file handling beyond truncation.
- Multiple/threaded prompt notes (decision #2 = single field).
- Changing the suggestion JSON schema, the apply-suggestion flow, or requirement sourcing.
- Auto-re-run on new commits/webhooks (manual re-run only).

## 3. Acceptance criteria

**Edit + re-run (backend)**
- [ ] `PUT /hub/impact/{projectId}/{id}` accepts `{ name?, linkedPrs, linkedRequirementIds, customPrompt? }`,
      replaces those fields on the job, sets status `RUNNING`, and triggers the async run; returns the
      updated job (`200`).
- [ ] Re-run **replaces** `summary` + `suggestions` (fresh compute from the full current context).
- [ ] Updating a job whose status is `RUNNING` is rejected (`409`) — no concurrent runs.
- [ ] `id`/`projectId` mismatch → `404`; the portal proxies `PUT /projects/{projectId}/impact-analyses/{id}`.
- [ ] Existing create/get/list behavior unchanged.

**Richer context (backend)**
- [ ] Each linked PR contributes its **title + description/body** to the prompt (from linked-PR
      metadata; body captured when a PR is linked).
- [ ] Changed **Markdown** files contribute their **diff/patch** text (bounded, e.g. ≤ N KB each and a
      total cap); non-Markdown changed files contribute **filename only** (current behavior).
- [ ] `customPrompt`, when present, is included as explicit user instructions in the AI prompt.
- [ ] Context assembly is **bounded** (per-file and overall caps) and never throws on a missing
      body/patch — it degrades to what's available.

**PR filter / search + Edit (frontend)**
- [ ] In the PR-selection step (create + edit), a **repo dropdown** narrows the PR list to a chosen
      repo (options derived from the loaded PRs' `repoFullName`; includes an "All repos" option), and a
      **search box** filters by PR **title** or **number/ID**. Both compose with each other and with
      existing selection.
- [ ] Multiple PRs across different repos can be selected in one job (already supported; the filter
      makes it navigable).
- [ ] An **Edit** action on a job opens the modal **pre-populated** with the job's current PRs,
      requirements, and instructions; the user can add PRs/requirements and edit instructions; **Save**
      calls the update endpoint and the UI reflects `RUNNING` → updated result.
- [ ] An **"Instructions for the AI"** textarea appears in create + edit and round-trips to
      `customPrompt`.
- [ ] `npx tsc -b` clean; design-token styling preserved (no raw palette / no purple).

## 4. Approach (implementation sketch — not binding)
- **Schema:** `platform-core/.../db/migration/V10__impact_analysis_custom_prompt.sql` →
  `ALTER TABLE impact_analyses ADD COLUMN custom_prompt text;` + `customPrompt` field on
  `ImpactAnalysis`.
- **Agent controller:** add `PUT /{projectId}/{id}` (`update`) — load, guard non-`RUNNING`, set fields,
  save, `impactAnalysisService.runAnalysis(id, projectId)`. Reuse the existing `CreateImpactAnalysisRequest`
  shape (+ `customPrompt`) or a sibling `UpdateImpactAnalysisRequest`.
- **Service (`runAnalysis`):** extend PR loop — capture `prBody` (from stored linked-PR metadata) and,
  from the PR-files response, include `patch` for `*.md` files (bounded). Append `customPrompt` to the
  user message. Keep the credential-cascade token resolution (already fixed).
- **Portal proxy:** `PUT /{id}` → `agentClient.put().uri("/hub/impact/{projectId}/{id}")`.
- **Frontend (`ImpactAnalysesPage.tsx` + `api.ts`):** add `updateImpactAnalysis`; extend
  `CreateImpactAnalysisForm`/types with `customPrompt` and per-PR `prBody`; add repo `<Select>` + search
  `Input` to the PR step; make `CreateModal` double as an edit modal seeded from a job; add the
  instructions textarea. Store PR `body` in `linkedPrs` when selecting (it's already in the `/prs`
  payload).

## 5. Commands
- **Backend build/test:** `mvn -q -pl platform-agent -am test` (agent) and `-pl platform-core` (entity +
  migration compile). Package for deploy: `mvn -q -pl platform-agent -am package -DskipTests` and
  `-pl platform-portal`.
- **DB migration** runs via the `db-migrate` (Flyway) compose service on stack up.
- **Frontend:** `cd platform-portal/frontend && npx tsc -b`.
- **Deploy (local docker-compose):** rebuild + recreate `platform-agent` and `platform-portal`
  (`docker compose build … && docker compose up -d …`); `db-migrate` applies `V10`.

## 6. Project structure (touched)
```
platform-core/src/main/java/com/platform/core/domain/ImpactAnalysis.java        # + customPrompt
platform-core/src/main/resources/db/migration/V10__impact_analysis_custom_prompt.sql  # new
platform-agent/.../api/ImpactAnalysisController.java                             # PUT update + re-run
platform-agent/.../api/ImpactAnalysisService.java                               # md-diff + PR body + customPrompt
platform-agent/.../api/ImpactAnalysis{Controller,Service}Test.java              # tests
platform-portal/.../api/PortalImpactController.java                             # PUT proxy
platform-portal/frontend/src/pages/ImpactAnalysesPage.tsx                       # filter/search + edit + instructions
platform-portal/frontend/src/lib/{api.ts,types.ts}                              # updateImpactAnalysis + fields
```

## 7. Code style / conventions
- **Java:** constructor injection; reuse `GitHubApiClient`, `CredentialResolver`; keep per-PR
  `try/catch` + `log.warn`, never log tokens; bound all AI context (constants for md-patch and total
  caps). No response-shape change to `suggestions`.
- **SQL:** additive, nullable column; forward-only Flyway (`V10`, never edit shipped migrations).
- **Frontend:** Tailwind v4 **design tokens** + `ui/` primitives (`Select`, `Input`, `Button`); no raw
  palette, no purple/indigo; accessible labels + focus rings; `cn()` for class composition.

## 8. Testing strategy
- **Agent unit tests (JUnit5 + Mockito, mirror existing):**
  - update endpoint replaces fields + re-runs and overwrites summary/suggestions;
  - `409` when status is `RUNNING`; `404` on id/project mismatch;
  - `runAnalysis` includes PR body, includes `.md` file patch (and excludes non-md patch), and injects
    `customPrompt`; context bounded (truncation applied over cap).
- **Regression:** full `mvn -q -pl platform-agent test` green (30+ classes).
- **Frontend:** `npx tsc -b`; manual pass — repo filter + PR search narrow the list; Edit pre-fills and
  re-runs; instructions round-trip.
- No SPA unit runner (type-check is the gate, matching the codebase).

## 9. Boundaries
**Always**
- Preserve create/get/list and the `suggestions`/`CodabasePr` contracts; keep credential-cascade token
  resolution.
- Bound AI context (per-file + total) to protect token budget/cost; degrade gracefully on missing
  body/patch.
- Frontend stays presentation-consistent (tokens + `ui/` kit).

**Ask first**
- Any **further** schema change beyond the single additive `custom_prompt` column.
- Increasing context depth beyond Markdown diffs (e.g. full patches for all files) — token/cost impact.
- Changing the `suggestions` JSON schema or the apply-to-test-case flow.
- Auto-re-run triggers (webhooks/schedules).

**Never**
- Log or expose PATs/tokens; send unbounded diffs to the LLM.
- Break or silently drop existing analyses; edit an already-shipped Flyway migration.
- Allow concurrent runs on the same job (guard `RUNNING`).

## 10. Open questions (non-blocking; sensible defaults chosen)
- Markdown-diff caps: default proposal **≤ 8 KB per file, ≤ 32 KB total** md content (tunable).
- Editable statuses: allow edit on `DRAFT`/`COMPLETED`/`FAILED`; block only `RUNNING`.
- PR body source: captured at link time from the `/prs` payload (no extra GitHub call); if absent,
  skip (no hard fetch) unless you want a live fetch fallback.
