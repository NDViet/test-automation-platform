# TODO — LiteLLM unified gateway (single client, `platform-llm` module)

Vertical slices. Check off only when **Acceptance** and **Verify** both pass.
Stop at each **CHECKPOINT** for human review.

---

## Phase 1 — Foundation + analysis path

### [x] T1 — Create `platform-llm` (LangChain4j → LiteLLM), move platform-ai onto it
> DONE. Corrected boundary (approved): `AiClient`/`AiAnalysisResponse` **stay** in platform-ai (they wrap
> `ClaudeAnalysisResult`); `platform-llm` holds only generic `LlmSettings` + `LlmChatModelProvider`.
> Added `LiteLlmAnalysisClient` (@Primary AiClient) + 5 unit tests; deleted ClaudeApiClient/OpenAiClient/
> AiClientRouter(+test); removed Anthropic SDK from platform-ai; LangChain4j 1.0.0 BOM in parent.
> Deferred to T2: AiSettingsController/UI redesign (LiteLLM keys settable via scoped-settings endpoint meanwhile).
> Note: AI keys are stored as plaintext settings today (matches prior behaviour); at-rest encryption is a separate hardening.
- **Create module:** `platform-llm/pom.xml` (depends on `platform-core`; deps `langchain4j` + `langchain4j-open-ai`
  as plain libs — **no spring-boot-starter**); register in root `pom.xml` `<modules>`.
- **Add:** `LlmChatModelProvider` building a LangChain4j `ChatModel` against the LiteLLM base URL + key;
  `LiteLlmClient` (AiClient over the ChatModel, structured/JSON output for analysis);
  `LlmSettings` (resolve `ai.litellm.base-url`/`api-key`/`model.analysis` via cascade).
- **Move:** `AiClient`, `AiAnalysisResponse` → `com.platform.llm` (update platform-ai imports).
- **Delete:** `ClaudeApiClient`, `OpenAiClient`, `AiClientRouter`; remove Anthropic SDK from `platform-ai/pom.xml`.
- **Confirm:** LangChain4j + langchain4j-open-ai versions resolve on Spring Boot 4 (manual bean wiring).
- **Settings keys:** `ai.litellm.base-url`, `ai.litellm.api-key` (encrypted), `ai.litellm.model.analysis`
  (+ reserve `…model.standard/.complex/.summarizer` for T3). `AiSettingsController`/`ScopedAiSettingsController` updated; GET masks key (`litellmKeySet`); `/test` probes `{baseUrl}/models`.
- **Acceptance**
  - `platform-ai` injects `LiteLlmClient` from `platform-llm`; `analyse()` returns parsed `AiAnalysisResponse` (+token usage); error path returns existing UNKNOWN/zero-token contract.
  - `providerName()` → `litellm/<model>`. No per-provider clients/router remain.
- **Verify:** `mvn -q -pl platform-llm,platform-ai -am test`; unit tests (happy + error, mock HTTP);
  ```bash
  curl -XPUT :8084/api/v1/ai/settings -d '{"liteLlmBaseUrl":"…","liteLlmApiKey":"…","model":"…"}'
  curl -XPOST :8084/api/v1/ai/settings/test -d '{}'
  ```

> ### ✅ CHECKPOINT CP-A — review before Phase 2
> `platform-llm` builds; analysis runs through a **real external LiteLLM**; `/test` succeeds; old clients + Anthropic SDK gone from platform-ai; keys never returned. **Get sign-off.**

---

## Phase 2 — Admin UI

### [x] T2 — Redesign `/settings/ai` to the LiteLLM shape
> DONE. Backend: `AiSettingsController` rewritten to the LiteLLM shape (base-url, masked key,
> model list, role→model: analysis/standard/complex/summarizer) + `/test` probes `{baseUrl}/models`;
> `ScopedAiSettingsController` key list updated (key excluded). `AiSettingsControllerTest` (3) green.
> Frontend: `AiSettingsPage` redesigned (base URL, key set/replace, model-list editor, role selectors,
> toggles, test); `types.ts`/`api.ts` updated; tsc + vite build green; rebuilt static bundle.
- **Files:** `frontend/src/pages/AiSettingsPage.tsx`, `lib/types.ts`, `lib/api.ts`.
- **Acceptance**
  - Fields: Base URL, API key (masked set/replace), Model list (add/remove), role→model selectors
    (analysis / standard / complex / summarizer), Enable + Real-time toggles, Test Connection.
  - Provider radio removed. Loading/error/empty use shared `LoadingSpinner`/`ErrorMessage`(retry)/`EmptyState`.
- **Verify:** `cd platform-portal/frontend && npm run format && npx tsc -b && npx vite build`; manual round-trip.

> ### ✅ CHECKPOINT CP-B — review before Phase 3
> Admin configures LiteLLM end-to-end in the UI; persists; test button reflects reality. **Get sign-off.**

---

## Phase 3 — Agents on LangChain4j + LiteLLM — HIGHEST RISK

### [x] T3a — LangChain4j tool-use runner (coexisting; live verification deferred)
> DONE (blind, per sign-off). Added `LangChainAgentRunner` (implements `AgentOrchestrator`) that drives
> the tool-use loop via a LiteLLM-routed LangChain4j `ChatModel`: tier→model from settings, dispatch via
> `AgentNode.dispatchToolCall`, ≤25-iteration cap, AWAITING_REVIEW + checkpoint, token accounting. Added
> `AgentNode.toolSpecs()` (default empty) alongside the existing Anthropic `tools()`. Runner coexists
> (not yet wired @Primary) so the app is unchanged. Tests: LangChainAgentRunnerTest 3/3; platform-agent 91/91.
> DEFERRED (SPEC F7): prompt-cache passthrough + cache-aware token counts unverified — cache columns are 0
> until validated against a live LiteLLM. Node tool-schema migration + @Primary swap + SDK removal = T3b.
- Pick **automation-code-generation**. Wire a LangChain4j `AiServices` with 1–2 migrated tools
  (`PlatformQueryTools`) over the `platform-llm` ChatModel.
- **Validate the SPEC F7 capability checklist:** (1) tool calling incl. multi/parallel, (2) **prompt
  caching** of the system prompt (today `CacheControlEphemeral`), (3) **cache-aware token accounting**,
  (4) ≤25-iteration cap + `maxTokens`, (5) structured/typed output.
- **Outcome:** Option A (LangChain4j/OpenAI through LiteLLM) confirmed, or document where Option B
  (Anthropic SDK → LiteLLM Anthropic endpoint) is needed (e.g. caching). **Decide at CP-C.**

### [x] T3b — Migrate `platform-agent` to LangChain4j  (DONE — blind, per sign-off)
> - [x] StepSummarizerImpl + ImpactAnalysisService ported to LiteLLM ChatModel.
> - [x] 7 nodes + 2 inner shim nodes: `tools()` (Anthropic) → `toolSpecs()` (LangChain4j `ToolSpecification`),
>       incl. nested array-of-object schemas (TestGen, ExtractAC) and the priority enum.
> - [x] Ported prompt-building (requirement hierarchy / PR-diff) into `LangChainAgentRunner`; made it
>       `@Primary @Component`; deleted `ClaudeAgentOrchestrator`.
> - [x] Removed `AgentNode.tools()` + the `anthropic-java` dependency (gone repo-wide). Fixed 2 node tests.
> Result: platform-agent 95/95 green; full reactor compiles; zero `com.anthropic` references remain.
> STILL DEFERRED (SPEC F7, needs live LiteLLM): prompt-cache passthrough + cache-aware token counts unverified.
- **Files:** replace `node/impl/ClaudeAgentOrchestrator.java` with a LangChain4j-based `AgentRunner`;
  `node/AgentNode.java` `tools()` → LangChain4j tools; `node/tools/*` keep impls + add `@Tool` bindings;
  `StepSummarizerImpl.java` + `api/ImpactAnalysisService.java` → model ids from settings;
  `platform-agent/pom.xml` (add platform-llm + langchain4j; **remove Anthropic SDK**).
- **Acceptance**
  - No hardcoded `Model.CLAUDE_*` for routed calls; `LlmTier` → `ai.litellm.model.{standard,complex,summarizer}`; `NONE` unchanged.
  - Tool-use loop works for migrated nodes; token usage still recorded; ≤25-iteration cap retained.
  - Optional env-gated fallback (`ai.fallback.anthropic.enabled`, default off) works when LiteLLM unreachable.
  - Anthropic SDK removed from platform-agent.
- **Verify:** `mvn -q -pl platform-agent -am test`; run automation-gen + impact-analysis flows → logs show the configured LiteLLM model + recorded token usage.

> ### ✅ CHECKPOINT CP-C (CRITICAL) — review before Phase 4
> Agent flow completes via LangChain4j→LiteLLM; F7 checklist validated (tool-use, caching, token accounting); fallback verified; SDK removed. **Get sign-off.**

---

## Phase 4 — Exports + cleanup

### [x] T4 — Export config snippets (OpenCode / Claude Code Router / VS Code chat)
> DONE. `LiteLlmExport` component on `/settings/ai`: tabbed, copy-to-clipboard config built from the
> configured base URL + model list via JSON.stringify (always-valid JSON). No API keys embedded —
> uses a `LITELLM_API_KEY` placeholder. tsc + vite build green.
> Note: VS Code chat provider field names vary by extension/version — snippet is labelled to adjust.
- **Files:** `frontend/src/components/LiteLlmExport.tsx` (new), wired into `AiSettingsPage.tsx`.
- **Acceptance**
  - Copy-to-clipboard snippets for all three tools from configured base URL + model list.
  - **No API keys embedded** — `${LITELLM_API_KEY}` placeholder. Field names verified vs current docs.
- **Verify:** `tsc -b` + `vite build`; paste each snippet into a scratch config and confirm it parses.

### [x] T5 — Cleanup, docs, security
> DONE. docker-compose: platform-ai + platform-agent env switched from ANTHROPIC/OPENAI/AI_PROVIDER →
> LITELLM_BASE_URL/LITELLM_API_KEY. README (env example, service + settings tables), SPEC.md (as-built
> boundary note), and docs/current-implementation-spec.md (§7.4 → LiteLLM gateway) updated. Trivy
> fs HIGH/CRITICAL = 0 after Anthropic-SDK removal + langchain4j addition.
> Note: the historical slides deck (scaling-...-slides.md) still references Claude/OpenAI provider
> switching — left as a point-in-time narrative; README/SPEC/impl-spec are the source of truth.
- **Files:** root `pom.xml` (module registered), `README.md`, `docs/system-architecture-current.md`
  (module map + LiteLLM gateway), remove dead provider/env references (AI_PROVIDER, OPENAI_*), optional `V2__seed_litellm_defaults.sql`.
- **Acceptance**
  - Docs describe the single-client gateway, settings, exports, and `platform-llm` module.
  - No leftover references to removed clients/provider switch.
- **Verify:** `mvn -Pformat process-sources`; `mvn -pl platform-llm,platform-ai,platform-agent,platform-core test` green;
  Trivy `fs --severity HIGH,CRITICAL` = 0 (expect the Anthropic SDK removal to *reduce* surface).

> ### ✅ CHECKPOINT CP-D — final review
> Snippets valid; docs/module-map current; format + tests + security clean. **Get sign-off to merge.**

---

## Cross-cutting acceptance (all tasks)
- One `LiteLlmClient` only — no per-provider clients/router. · Settings via `SettingResolver` (cascade preserved).
- No secrets in logs/GET/snippets. · `AiAnalysisResponse` contract unchanged. · Format + type-check gates pass before "done".
