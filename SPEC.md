# SPEC — LiteLLM as the unified LLM gateway (`/settings/ai`)

> Status: DRAFT — awaiting confirmation before implementation.
> Owner: platform team · Scope: `platform-ai`, `platform-agent`, `platform-portal`, `platform-core`

## 1. Objective

Make **LiteLLM the single LLM gateway** for the entire platform. Every model call —
failure analysis (`platform-ai`) and agentic workflows (`platform-agent`) — goes through
one OpenAI-compatible LiteLLM endpoint instead of talking to Anthropic/OpenAI directly.

The AI settings page (`http://localhost:8085/settings/ai`) becomes a LiteLLM configuration
screen that uses the **same configuration shape developers already know** from OpenCode,
Claude Code Router, and VS Code chat (base URL + API key + a named model list), and can
**export ready-to-paste config** for those three tools so the platform and a developer's
local tooling point at the same gateway and models.

### Target users
- **Platform admins / QA leads** — configure which models back analysis and agents, per Org/Team/Project.
- **Developers** — reuse the platform's LiteLLM gateway in their own tools (OpenCode, Claude Code Router, VS Code chat) via exported config.

### Non-goals (this iteration)
- Running/hosting a LiteLLM proxy container (we connect to an **external** endpoint).
- Cost dashboards, usage quotas, or rate-limit management (LiteLLM owns these).
- Removing the Anthropic Java SDK path entirely (kept only as an optional, env-gated fallback).

## 2. Background — current state

- `AiClient` (interface) → `AiClientRouter` (`@Primary`) selects `ClaudeApiClient` (Anthropic SDK)
  or `OpenAiClient` (OpenAI REST) from the `ai.provider` setting, with an Org→Team→Project
  cascade via `SettingResolver`.
- `OpenAiClient` hardcodes `https://api.openai.com/v1/chat/completions`. LiteLLM is
  OpenAI-compatible, so this client is the reuse seam — it needs a **configurable base URL**.
- `platform-agent` selects models by `LlmTier` (`STANDARD`→claude-sonnet, `COMPLEX`→claude-opus),
  hardcoded to Claude.
- Settings persist in `platform_settings` under `ai.*` keys; surfaced by `AiSettingsController`
  (`/api/v1/ai/settings`) + `ScopedAiSettingsController`; UI at `platform-portal/frontend/src/pages/AiSettingsPage.tsx`.

## 3. Decisions (from clarification)

| Topic | Decision |
|---|---|
| Deployment | **External LiteLLM endpoint only** — user provides base URL + key. No new container. |
| Config standard | **OpenAI-compatible shape** (baseURL + apiKey + named model list) **+ export snippets** for OpenCode, Claude Code Router, VS Code chat. |
| Agent routing | **Agents route through LiteLLM**; tier (STANDARD/COMPLEX) → configurable LiteLLM model names. |
| Provider model | **LiteLLM is the single gateway.** UI provider switch (anthropic/openai) is removed; those become routes *inside* LiteLLM. |
| Client | **One client** for the whole platform: a single OpenAI-compatible `LiteLlmClient`. The model id (or LiteLLM alias) selects Claude/GPT/Gemini/etc.; LiteLLM does the translation. `ClaudeApiClient`, `OpenAiClient`, and `AiClientRouter` are removed. |
| Module org | New shared module **`platform-llm`** (depends on `platform-core`) hosts the LiteLLM-backed LangChain4j chat model (`LlmChatModelProvider`) + settings resolution (`LlmSettings`). `AiClient`/`AiAnalysisResponse` stay in **`platform-ai`** (analysis domain). **`platform-ai`** (failure analysis) and **`platform-agent`** (agentic workflows) both depend on `platform-llm`. Anthropic Java SDK dependency removed from both. |
| Agent SDK | **LangChain4j** is the agent layer (Spring Boot 4-safe; plain library, no spring-boot-starter). `platform-llm` exposes a LangChain4j `ChatModel`/`StreamingChatModel` configured against the LiteLLM **OpenAI-compatible** endpoint. `platform-agent` uses LangChain4j `AiServices` (tool-use loop, structured output, memory); the custom `ClaudeAgentOrchestrator` loop is replaced. `platform-ai` uses LangChain4j structured output for classification. |
| Default | **LiteLLM is the default** provider. |
| Fallback | Optional, env-gated **direct-Anthropic fallback** (default OFF), implemented inside `platform-llm`. |

## 4. Core features & acceptance criteria

### F1 — Single LiteLLM chat model in shared `platform-llm` module
> AS-BUILT note: to keep the dependency direction sound, `platform-llm` holds only the **generic**
> LLM access — `LlmChatModelProvider` (LangChain4j `ChatModel` → LiteLLM) + `LlmSettings`. The
> analysis-specific `AiClient`/`AiAnalysisResponse` **stay in `platform-ai`** (they wrap
> `ClaudeAnalysisResult`); `platform-ai` adds `LiteLlmAnalysisClient` over the shared model, and
> `platform-agent` uses `LangChainAgentRunner`. There is one LLM client path, via LiteLLM.
- New module `platform-llm` (depends on `platform-core`) provides the LangChain4j `ChatModel` pointed
  at the LiteLLM OpenAI-compatible endpoint + settings resolution.
- "OpenAI-compatible" is just the wire format LiteLLM exposes — no per-provider clients. The model
  id (or LiteLLM alias) routes to Claude/GPT/Gemini/etc. inside LiteLLM.
- `ClaudeApiClient`, `OpenAiClient`, `AiClientRouter`, `ClaudeAgentOrchestrator` deleted; Anthropic SDK removed from both service poms.
- **AC1.1** With a valid LiteLLM base URL + key + model, `analyse()` returns a parsed
  `AiAnalysisResponse` with token usage; on error returns the UNKNOWN/zero-token response (existing contract).
- **AC1.2** `providerName()` returns `litellm/<model>`.
- **AC1.3** Both `platform-ai` and `platform-agent` inject the same `LiteLlmClient` from `platform-llm`.

### F2 — LiteLLM as the routed default
- `AiClientRouter` resolves `ai.provider=litellm` (new default) and routes to `LiteLlmClient`.
- Org→Team→Project cascade still applies to base URL, key, and model.
- **AC2.1** Fresh install defaults to `litellm`. **AC2.2** Changing model/base URL/key in the
  Portal takes effect without a service restart (matches current live-reload behaviour).

### F3 — Multiple models + per-tier agent routing
- Settings hold a **model list** (id + optional label) plus role→model mappings:
  - `ai.litellm.model.analysis` (failure classification)
  - `ai.litellm.model.standard` (agent STANDARD tier)
  - `ai.litellm.model.complex` (agent COMPLEX tier)
- `platform-agent` model selection reads these instead of hardcoded Claude names; `LlmTier.NONE` unchanged.
- **AC3.1** An agent run logs and uses the configured LiteLLM model for its tier.
- **AC3.2** Multiple models can be saved and selected per role from the model list.

### F4 — Settings page redesign (OpenAI-compatible shape)
- `/settings/ai` exposes: **Base URL**, **API key** (masked, set/replace pattern as today),
  **Model list** (add/remove), **role→model** selectors (analysis / standard / complex),
  Enable + Real-time toggles (unchanged), **Test Connection**.
- Provider radio (anthropic/openai) is **removed**.
- **AC4.1** Test Connection hits `{baseUrl}/models` (or a cheap completion) and reports success/failure.
- **AC4.2** Empty/error/loading states use the shared `EmptyState`/`ErrorMessage` (with retry) components.

### F5 — Export config for external tools
- A "Use this gateway in your tools" panel generates copy-to-clipboard config for:
  - **OpenCode** — `opencode.json` provider block (`@ai-sdk/openai-compatible`, `options.baseURL`, `apiKey`, `models`).
  - **Claude Code Router** — `~/.claude-code-router/config.json` `Providers[]` (`api_base_url`, `api_key`, `models`) + `Router.default`.
  - **VS Code chat** — OpenAI-compatible / BYOK model entry (baseURL + model id; key entered by the user).
- API keys are **never** embedded in exported snippets — show a `${LITELLM_API_KEY}` placeholder.
- **AC5.1** Each snippet is valid for its tool and uses the configured base URL + model list.
- **AC5.2** Exact field names verified against each tool's current docs at implementation time.

### F7 — LangChain4j agent layer (capability preservation)
The current `ClaudeAgentOrchestrator` is already a tool-use loop (≤25 iterations, `node.tools()` =
Anthropic `Tool` objects, prompt caching via `CacheControlEphemeral`, cache-aware token/cost accounting).
LangChain4j replaces the loop while preserving power.
- `platform-llm` exposes a LangChain4j `ChatModel` (langchain4j-open-ai) pointed at LiteLLM base URL + key.
- `platform-agent` defines tools as LangChain4j tools (`@Tool` / `ToolSpecification`); the existing tool
  *implementations* (`PlatformQueryTools`, `PlatformInsightTools`, `GitHubApiClient`) are reused.
- Per-node flows (TestCaseGeneration, AutomationCodeGeneration, Healing, Analysis…) use `AiServices`
  with their tools + a **verify loop** (generate → run → observe → fix) where applicable.
- **Capability checklist — must survive the migration (T3 spike gates these):**
  1. **Tool calling** through LiteLLM (incl. multi-/parallel-tool turns) — primary.
  2. **Prompt caching** of the large system prompt (today `CacheControlEphemeral`). Verify LangChain4j +
     LiteLLM passes Anthropic `cache_control`; if not, accept higher cost OR use Option B for cached flows.
  3. **Cache-aware token accounting** (input / cacheWrite / cacheRead / output → cost). Preserve or
     document the approximation; this feeds existing cost reporting.
  4. **Max tool iterations** safety cap (≤25) and `maxTokens` retained.
  5. **Structured/typed output** for generated artifacts (TestCase, automation file) instead of regex.
- **AC7.1** One real flow (automation-code-generation) runs through LangChain4j→LiteLLM with tools and
  completes. **AC7.2** Token usage is still recorded. **AC7.3** `LlmTier` still selects the model id.

### ADR-001 — Agent SDK: LangChain4j (accepted)
- **Context:** need an agent loop + tool-use + structured output on a Spring Boot 4 / Java stack, routed
  through the single LiteLLM gateway.
- **Options:** Spring AI (idiomatic but Spring Boot 4 support lags), LangChain4j (framework-agnostic,
  OpenAI-compatible, Spring Boot-version-independent), keep custom (most maintenance).
- **Decision:** **LangChain4j**, used as a plain library (no spring-boot-starter) to avoid Spring Boot 4
  coupling, configured against LiteLLM's OpenAI-compatible endpoint.
- **Consequences:** custom orchestrator retired; tool definitions migrate to LangChain4j; prompt-caching
  and cache-token accounting must be re-validated (see checklist). Option B (Anthropic SDK → LiteLLM
  Anthropic endpoint) remains the documented fallback for any flow where caching can't pass through.

### F6 — Migration & backward compatibility
- Flyway migration / setting-resolver default: introduce `ai.provider=litellm` default; keep reading
  legacy `ai.anthropic.api-key` / `ai.openai.api-key` for the optional fallback only.
- **AC6.1** Existing installs with `ai.provider=anthropic|openai` keep working until an admin migrates
  (router still recognizes legacy values), with a one-time UI notice to move to LiteLLM.

## 5. Settings keys (platform_settings, cascade-aware)

| Key | Meaning | Default |
|---|---|---|
| `ai.enabled` | master enable | `false` |
| `ai.realtime.enabled` | realtime analysis | `false` |
| `ai.provider` | `litellm` (new default; legacy `anthropic`/`openai` still read) | `litellm` |
| `ai.litellm.base-url` | LiteLLM OpenAI-compatible base URL | — |
| `ai.litellm.api-key` | LiteLLM master/virtual key (encrypted at rest) | — |
| `ai.litellm.models` | JSON array of `{id,label}` | `[]` |
| `ai.litellm.model.analysis` | model id for failure analysis | first model |
| `ai.litellm.model.standard` | model id for agent STANDARD tier | first model |
| `ai.litellm.model.complex` | model id for agent COMPLEX tier | first model |
| `ai.fallback.anthropic.enabled` | env-gated direct-Claude fallback | `false` |

API keys follow the existing **encrypted-credential** handling and are never returned by GET (only `*KeySet` booleans).

## 6. Commands

```bash
# Backend (build/test the affected modules)
mvn -q -pl platform-ai,platform-agent,platform-core -am -DskipTests install
mvn -q -pl platform-ai,platform-agent test

# Frontend (portal)
cd platform-portal/frontend
npm run format && npx tsc -b && npx vite build

# Format everything (Java + frontend)
mvn -Pformat process-sources

# Security re-scan after any dependency change
docker run --rm -v "$PWD":/src:ro -v "$HOME/.m2":/root/.m2:ro -v trivy-cache:/root/.cache/ \
  aquasec/trivy:latest fs --scanners vuln --offline-scan --severity HIGH,CRITICAL /src

# Run locally (point at an external LiteLLM)
docker compose --profile services up -d --build platform-ai platform-agent platform-portal
```

## 7. Project structure (add / change)

```
platform-llm/                     # NEW MODULE (depends on platform-core)
  pom.xml                         # deps: langchain4j, langchain4j-open-ai (plain libs, no spring-boot-starter)
  src/main/java/com/platform/llm/
    # (AiClient/AiAnalysisResponse stay in platform-ai — they wrap the analysis domain)
    LlmChatModelProvider.java     # NEW — builds LangChain4j ChatModel (langchain4j-open-ai) → LiteLLM base URL/key
    LiteLlmClient.java            # NEW — AiClient impl over the ChatModel (analysis/structured output)
    LlmSettings.java              # NEW — resolve base-url/key/model.* via SettingResolver (cascade)
    AnthropicFallbackClient.java  # NEW (optional, env-gated, default off)

platform-ai/src/main/java/com/platform/ai/
  client/ClaudeApiClient.java, openai/OpenAiClient.java, client/AiClientRouter.java  # DELETE
  api/AiSettingsController.java       # CHANGE — base-url/models/role-model fields; /models test
  api/ScopedAiSettingsController.java # CHANGE — same fields, scoped
  pom.xml                            # CHANGE — add platform-llm; remove Anthropic SDK

platform-agent/src/main/java/com/platform/agent/
  node/impl/ClaudeAgentOrchestrator.java  # REPLACE — LangChain4j AiServices tool-use loop (rename → AgentRunner)
  node/AgentNode.java                      # CHANGE — tools() returns LangChain4j tools (was Anthropic Tool)
  node/tools/*.java                        # KEEP impls; ADD LangChain4j @Tool bindings
  node/impl/StepSummarizerImpl.java        # CHANGE — model id from settings (was CLAUDE_HAIKU_4_5)
  api/ImpactAnalysisService.java           # CHANGE — model id from settings
  pom.xml                                  # CHANGE — add platform-llm + langchain4j; remove Anthropic SDK

platform-portal/frontend/src/
  pages/AiSettingsPage.tsx        # CHANGE — LiteLLM shape, model list, role mapping, exports
  components/LiteLlmExport.tsx    # NEW — OpenCode / Claude Router / VS Code snippet generator
  lib/types.ts, lib/api.ts        # CHANGE — new AiSettings shape + endpoints

pom.xml                           # CHANGE — register platform-llm module
docs/system-architecture-current.md, README.md  # CHANGE — document LiteLLM gateway + module map
```

## 8. Code style

- Match surrounding code: Java formatted by **Spotless/google-java-format** (`mvn -Pformat`);
  frontend by **Prettier** (no semicolons, single quotes, `arrowParens: avoid`, printWidth 100).
- Reuse existing seams — **do not** add a second HTTP/JSON stack; extend `OpenAiClient`'s path.
- Secrets: reuse the platform credential encryption; never log keys; GET never returns key values.
- Frontend: keep the four-state pattern (loading/error/empty/data) and shared `ErrorMessage`/`EmptyState`.
- New settings keys go through `SettingResolver` so the Org→Team→Project cascade is preserved.

## 9. Testing strategy

- **Unit (platform-ai):** `LiteLlmClient.analyse()` happy path + error path (mock HTTP);
  `AiClientRouter` selects litellm by default and honours the cascade; legacy provider values still route.
- **Unit (platform-agent):** tier→model resolution reads the configured LiteLLM model ids; `NONE` unchanged.
- **Contract:** `AiSettingsController` GET masks keys; PUT persists base-url/models/role maps; `/test` reports success/failure.
- **Frontend:** type-check (`tsc -b`) + build; manual verify of settings round-trip and that each exported snippet parses as valid JSON for its tool.
- **Regression:** existing failure-analysis flow still produces `AiAnalysisResponse`; Trivy HIGH/CRITICAL stays at 0 after any dep change.
- Verify end-to-end against a real external LiteLLM endpoint before merge.

## 10. Boundaries

**Always**
- Keep LiteLLM as the single configured gateway in the UI; route all model calls through `AiClient`.
- Preserve the Org→Team→Project settings cascade and encrypted-key handling.
- Keep backward-compat reads for legacy `ai.provider`/keys; provide a migration default.
- Run `mvn -Pformat` and `tsc -b`/`vite build` before declaring done.

**Ask first**
- Adding a LiteLLM **container** to docker-compose (currently out of scope — external only).
- Removing the Anthropic Java SDK dependency entirely (kept for optional fallback).
- Any change to the `AiClient` public contract or `AiAnalysisResponse` shape.
- Schema changes beyond additive settings keys.

**Never**
- Embed real API keys in exported snippets, logs, or GET responses.
- Hardcode model names or base URLs in `platform-agent` (must come from settings).
- Break the existing failure-analysis API or the nightly batch job behaviour.
- Send platform data to any endpoint other than the configured LiteLLM gateway.

## 11. Open items to confirm during implementation
- Exact current config field names for OpenCode / Claude Code Router / VS Code chat (verify against their docs).
- Whether the optional direct-Anthropic fallback should be per-project or global-only (proposed: global env flag).
- Whether `/settings/ai` model list should be free-text or fetched live from `{baseUrl}/models`.
