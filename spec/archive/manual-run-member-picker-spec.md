# SPEC — Searchable member picker for Create Manual Test Run (Triggered By / Default Assignee)

> Status: **DRAFT — awaiting confirmation.** Small UX + API fix. Prior feature specs are archived under
> `spec/archive/`.

## 1. Objective

In **Create Manual Test Run** (New Manual Run modal), the **Triggered By** and **Default Assignee**
dropdowns are plain native `<select>`s that list only `displayName || username` and offer **no way to
search** — so on a project with many members you cannot find a person, and you **cannot search by
email** at all (email isn't even returned by the API). Make both fields a **searchable member
picker** that filters as you type across name, username, **and email**.

### Target users
QA engineers / leads creating manual test runs who need to quickly find the right person by typing part
of their name or email.

### Root cause (verified in code)
- Backend `ExecutionPlanningService.assignableUsers()` maps `User → AssignableUser(id, username,
  displayName)` — **email is dropped** (the `User` entity has `getEmail()`), and it returns
  `userRepo.findAllByOrderByUsernameAsc()` filtered to enabled — i.e. **all enabled users, not
  project-scoped**. Endpoint: `GET …/projects/{projectId}/assignable-users` (ingestion), proxied by
  `PortalExecutionPlanningController` (pass-through). RBAC already enforced on the ingestion route.
- Frontend `TestExecutionPage.tsx` New Manual Run modal renders two native `<select>`s
  (`Triggered By` → stores `username`; `Default Assignee` → stores user `id`) over the
  `api.assignableUsers(projectId)` list; `AssignableUser` type = `{id, username, displayName}` (no
  email). The same `assignableUsers` list also drives the run **EditScope → Assignee** select in
  `TestRunExecutionPage.tsx`.
- The UI kit (`components/ui/`) has `Select` but **no combobox/typeahead** — a new component is needed.

### Assumptions locked for this draft (clarifying questions went unanswered — confirm or correct)
1. **Member scope = project members only.** The report says "member **in project**", and the current
   "all enabled users" behavior contradicts that. `assignableUsers` becomes **project-scoped** (users
   with any role in the project via `user_roles`). ⚠️ Trade-off: a user with only an org-level role and
   no project role would no longer appear. If you'd rather keep listing all enabled users (smaller
   change), say so and only the email + search parts remain.
2. **Rollout = the shared picker, applied where `assignableUsers` is used.** Build one reusable
   `UserPicker` and use it for **Triggered By** + **Default Assignee** (Create Manual Test Run) **and**
   the run **EditScope → Assignee**, since they share the same list/query.
3. **All three fields must resolve to a listed member — no free text.** A manual test run is
   human-triggered by definition (a CI cannot trigger a manual execution today), so **Triggered By**
   must be a selected project member (emits `username`), exactly like **Default Assignee** / **EditScope
   Assignee** (emit a user `id`). The picker only accepts a member; a typed value that matches nobody is
   not submittable. (Confirmed by the user.)

## 2. Scope

**In:**
1. **API** — add `email` to `AssignableUser` (ingestion DTO + mapping). Portal proxy is pass-through
   (no change). Scope the list to project members (assumption 1).
2. **UI kit** — a reusable **`UserPicker`** combobox: a text input that filters an `AssignableUser[]`
   by name / username / email substring (case-insensitive), keyboard-navigable, showing
   `displayName` with `email`/`username` as secondary text. It **only yields a listed member** (no free
   text); a parameter chooses which field it emits — the member's `id` (Assignee) or `username`
   (Triggered By) — and it exposes the current selection's label for display.
3. **Wire-in** — replace the three native `<select>`s (Create Manual Test Run: Triggered By +
   Default Assignee; run EditScope: Assignee) with `UserPicker`. Preserve current submit payloads
   (`triggeredBy` = a member's `username`; `assigneeUserId` = a member's user `id`).
4. **Types** — add `email: string | null` to the `AssignableUser` TS type.

**Out:** changing how runs/executions are created or assigned server-side (the run-assignee feature is
done); adding/removing members; avatars/presence; a global user directory; paginated/async member
search (client-side filter over the already-fetched list is sufficient at current member counts).

**Data:** none — no schema change. `User.email` already exists; project membership already exists in
`user_roles`.

**Endpoints:** unchanged paths. `GET …/assignable-users` response gains an `email` field and (per
assumption 1) is filtered to project members. RBAC unchanged (already guarded on the ingestion route).

## 3. Acceptance criteria
- Typing part of a **name, username, or email** in Triggered By / Default Assignee / EditScope Assignee
  filters the list to matching members; selecting one fills the field.
- `GET …/assignable-users` returns `email` for each user, and (assumption 1) only users who are members
  of the project.
- **Default Assignee** and **EditScope Assignee** still submit a valid user `id` (or empty =
  unassigned); an unassigned run still fills nothing. **Triggered By** submits a selected member's
  `username` (no free-typed values — a manual run is human-triggered).
- Keyboard: arrow up/down moves the highlight, Enter selects, Esc closes; clicking outside closes.
  Empty query shows the full (project-scoped) list.
- `npx tsc -b` clean; backend module tests green; design tokens + `ui/` kit (no raw palette).

## 4. Commands
- **Backend:** `mvn -q -pl platform-ingestion -am test` (service + DTO live in ingestion); targeted
  `mvn -pl platform-ingestion -Dtest='ExecutionPlanningServiceTest#…' test`.
- **Frontend:** `cd platform-portal/frontend && npx tsc -b`.
- **Deploy:** `deploy-local` skill — rebuild `platform-ingestion` + `platform-portal` (`-am`), no
  migration, `up -d --no-deps`, verify.

## 5. Project structure (touched)
```
platform-ingestion  # ExecutionPlanningService.AssignableUser + mapping (+ project-scoped query);
                    #   ExecutionPlanningService (list assignable users for a project)
platform-portal(FE) # components/ui/UserPicker.tsx (new reusable combobox) + index export;
                    #   pages/TestExecutionPage.tsx (Triggered By + Default Assignee),
                    #   pages/TestRunExecutionPage.tsx (EditScope Assignee); lib/types.ts (email)
```

## 6. Code style / testing / boundaries
- **Java:** reuse `UserRepository`; keep the `AssignableUser` record (add `email`); project-scoping via
  the existing role/membership repository (verify the exact query source before writing — mirror how
  other project-scoped user lists are built). JUnit5 + Mockito + AssertJ; strict stubs.
- **Frontend:** Tailwind v4 design tokens + `ui/` kit; hooks before early returns; controlled input;
  accessible (role="combobox"/listbox, aria-activedescendant); reuse across the three sites; `tsc -b`
  gate. Keep the existing submit contracts (`triggeredBy` = username, `assigneeUserId` = id) unchanged;
  all three fields yield only a listed member (no free text).
- **Testing:** backend — `assignableUsers` includes email and is limited to project members (a non-member
  enabled user is excluded); frontend — `tsc -b`, plus manual verification of filter-by-email in the
  running app.
- **Boundaries — Always:** keep RBAC on the endpoint; design-token UI; preserve current submit payloads.
  **Ask first:** changing member scope away from "project members" (assumption 1) or applying the picker
  beyond the three named sites. **Never:** expose password/hash fields in the user list; add a schema
  migration for this (none needed); break the unassigned-run path.
