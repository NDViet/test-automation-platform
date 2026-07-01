# SPEC — Portal UI: unified design system + page migration

> Status: **DRAFT — awaiting confirmation.** Auto-generated to formalize the in-flight Portal UI
> refactor so the remaining pages migrate against a real spec. Prior specs archived under
> `spec/archive/` (incl. the AWS ECS deployment spec).

## 1. Objective

Unify the Portal SPA (`platform-portal/frontend`, React 19 + Vite + **Tailwind v4** + cva) under a
single **design-token system** and a **reusable `components/ui/` primitive kit**, on a **refreshed
palette**, with **better space utilization**, and **migrate all 35 pages** onto it — so the portal
reads as one intentional product, not 35 hand-rolled pages.

### Target users
- **Portal users** (QA engineers, leads) — get a consistent, denser, easier-to-scan UI.
- **Frontend maintainers** — build new pages from primitives instead of copy-pasting Tailwind soup.

## 2. Resolved decisions (locked this session)
1. **Refreshed palette** via Tailwind v4 `@theme` tokens in `src/index.css`: cool-neutral canvas,
   **azure primary `#1f6feb`** (deliberately *not* purple/indigo — avoids the "AI aesthetic"; green/
   amber/red stay reserved for QA status), semantic status ramp (success/warning/danger/info/neutral,
   each fg+bg+border), one radius scale, subtle elevation.
2. **In-house `ui/` kit** (cva + `cn()`, **no** shadcn/new dependency): `Button`, `Card`(+parts),
   `PageHeader`, `StatusBadge`, `Input`, `Select`, `Table`. Accessible, `forwardRef`.
3. **Responsive width**: `AppLayout` + `PageWidth` context → per-page `default` (1280px, forms) /
   `wide` (1600px, tables/dashboards) / `full`; opt in with `usePageWidth('wide')`.
4. **Status coloring centralized**: `lib/status.ts` (typed `StatusVariant` mappings) + tokenized
   `lib/utils` color helpers — replacing per-page `statusColor`/`priorityColor` copies.
5. **Incremental, per-page migration**: one page per commit; verification gate is `npx tsc -b`
   (the SPA has **no unit-test runner**); presentation-only (no behavior/logic/data changes).
6. **Kill the AI aesthetic**: no purple/indigo primary, flat surfaces + subtle shadows, consistent
   8px radius, tighter padding, denser tables.

## 3. Scope & status

### Foundation — DONE
- `src/index.css` `@theme` tokens; `components/ui/` kit + barrel; `lib/status.ts`.
- Responsive `AppLayout` + `components/layout/PageWidth.tsx`.
- Shared chrome tokenized (re-skins every page): `EmptyState`, `ErrorMessage`, `LoadingSpinner`,
  `StatCard`, `ProjectLayout` filter bar, and `lib/utils` color helpers.

### Page migration — 19 / 35 done
- **Done:** Users, QualityDashboard, TestCases *(scoped: header+badges)*, Login, Alerts, OrgSelect,
  ChangePassword, MappingRules, ApiKeys, PRAnalyses, TaskAgents, OrgOverview, RunDetail,
  CoverageMatrix, Releases, TestExecutionDashboard, AdoStructure, GitHubWorkflows, ReviewQueue.
- **Remaining (~15/16):** AdminIntegrations, AdoMapping, Agents, AiSettings, AutomatedTests,
  FlakyTests, ImpactAnalyses, Productivity, ProjectDetail, ProjectSettings, Requirements, Suites,
  TestExecution (TestExecutionPage), TestRunExecution, TestRuns — plus completing **TestCases** deep
  internals (create/edit + AI-generation modals).

## 4. Per-page migration checklist (acceptance)
For each page:
- [ ] Title uses `PageHeader` (icon + description + `actions` slot).
- [ ] Buttons → `Button`; inputs → `Input`; selects → `Select`; status pills → `StatusBadge`
      (variant via `lib/status`); surfaces → `Card`/token classes.
- [ ] **No raw `slate-*/blue-*/green-*/red-*/amber-*/purple-*` chrome classes** — semantic tokens
      only (`bg-surface`, `border-border`, `text-fg/-muted/-subtle`, `text-primary`, status tokens).
- [ ] **No purple/indigo** accents (moved to azure `primary`/`subtle`).
- [ ] Data-dense pages call `usePageWidth('wide')`; wide tables scroll in their own container.
- [ ] Accessible: labelled inputs, `aria-label` on icon-only buttons, visible focus rings.
- [ ] `npx tsc -b` clean; presentation-only (no logic/data change); one commit for the page.

## 5. Commands
- **Type gate (per page):** `cd platform-portal/frontend && npx tsc -b`
- **Visual check:** `npm run dev` (Vite dev server; do **not** run `vite build` casually — its
  `emptyOutDir` wipes the deployed `pw-trace` assets under `src/main/resources/static`).
- **Full build:** `npm run build` (`tsc -b && vite build`).
- **Format/lint:** `npm run format`, `npm run lint`.

## 6. Project structure (touched)
```
platform-portal/frontend/src/
  index.css                       # @theme design tokens
  components/ui/                   # Button, Card, PageHeader, StatusBadge, Input, Select, Table, index
  components/layout/               # AppLayout, PageWidth, ProjectLayout, Sidebar
  components/{EmptyState,ErrorMessage,LoadingSpinner,StatCard}.tsx   # tokenized
  lib/{status.ts,utils.ts}         # StatusVariant mapping + tokenized color helpers
  pages/*.tsx                      # 35 pages migrated incrementally
tailwind.config.js                 # v4-ignored (kept, inert); tokens live in index.css
```

## 7. Code style / conventions
- **Tailwind v4**: tokens in `@theme`; use **semantic token utilities**, never raw palette, for
  chrome. Variants via **cva**; compose classes with `cn()` (`clsx` + `tailwind-merge`).
- Composition over configuration for primitives; keep components focused (< ~200 lines).
- **WCAG 2.1 AA**: real `<button>`s, labelled inputs, focus-visible rings, meaningful empty/error/
  loading states.
- One page per commit; stage only that page's file(s); commit message `Portal UI: migrate <Page> …`.

## 8. Testing strategy
- **Type gate**: `npx tsc -b` must pass after every page (the established gate — no Jest/Vitest here).
- **Visual pass**: `npm run dev` to confirm the refreshed palette + layout render correctly (Tailwind
  v4 does **not** error on a mistyped token utility, so type-check alone is insufficient — a human/dev
  visual check is required before relying on it).
- **Final**: `npm run build` (full `tsc -b && vite build`) once migration completes.
- Optional: axe-core / keyboard tab-through on key pages.

## 9. Boundaries
**Always**
- Use design tokens + `ui/` primitives; keep per-page commit + `tsc` gate.
- Presentation-only — preserve every page's behavior, data flow, and query keys exactly.
- Keep it accessible; keep the azure/cool-slate direction consistent.

**Ask first**
- Changing token **values** (palette/radii/spacing) or the primary hue.
- Adding a dependency (e.g. shadcn/ui, a component lib) or a new primitive's API shape.
- Restructuring a page's **logic/state** (vs. restyling), or touching non-portal / backend code.
- Running `vite build` against the real `outDir` (wipes `pw-trace`).

**Never**
- Introduce a purple/indigo primary or the generic "AI aesthetic".
- Change application logic, API calls, or DB/data while restyling.
- `git add -A` blindly; break `tsc`; commit unsigned without permission.

## 10. Out of scope
- Backend / other frontends (adapters, testkit).
- Functional/behavioral changes, new features, or new pages.
- Deep re-architecture of page state management.
