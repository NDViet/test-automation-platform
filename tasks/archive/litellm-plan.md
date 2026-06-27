# PLAN — LiteLLM unified gateway (single client, `platform-llm` module)

Derived from `SPEC.md`. Read-only analysis done; no code changed. Vertical slices + checkpoints.

## 1. Grounding facts (from codebase)

- **One wire format, one client.** "OpenAI-compatible" is just the API shape LiteLLM exposes.
  A single `LiteLlmClient` (`POST {baseUrl}/chat/completions`) routes to Claude/GPT/Gemini/etc. by
  model id. No per-provider clients, no router.
- **Module deps today:** `platform-common` → `platform-core` (owns `SettingResolver`,
  `PlatformSettingRepository`) → both `platform-ai` and `platform-agent`. Both already depend on
  `platform-core`; both carry the **Anthropic Java SDK**.
- **platform-ai:** `AiClient`/`AiClientRouter`/`ClaudeApiClient`/`OpenAiClient` (OpenAiClient hardcodes
  the OpenAI URL). These collapse into the single client.
- **platform-agent:** `ClaudeAgentOrchestrator` is already a hand-rolled tool-use loop (≤25 iters,
  `node.tools()` = Anthropic `Tool`, **prompt caching** via `CacheControlEphemeral`, cache-aware token
  accounting); tiers → `Model.CLAUDE_*`. Replacing this loop with **LangChain4j `AiServices`** (over
  LiteLLM) and migrating the tools is the **highest-risk** work — caching + token accounting must survive.
- **Defaults in code**, only `V1__initial_schema.sql` exists → no schema migration required.

## 2. Target module organization

```
platform-common ─► platform-core ─► platform-llm ─┬─► platform-ai     (failure analysis)
                     (settings)     (THE client)   └─► platform-agent (agentic workflows)
                                                         └─► platform-portal (UI → REST)
```

- **platform-llm (new):** LangChain4j `ChatModel` (langchain4j-open-ai) → LiteLLM; `AiClient` +
  `AiAnalysisResponse` (moved), `LiteLlmClient` (AiClient over the ChatModel), `LlmSettings` (cascade),
  optional env-gated `AnthropicFallbackClient`. Uses LangChain4j as a **plain library** (no spring-boot-starter → Spring Boot 4-safe).
- **platform-ai:** analysis domain only; depends on platform-llm; deletes the 3 old clients + router;
  drops Anthropic SDK; uses LangChain4j structured output for classification.
- **platform-agent:** workflows only; depends on platform-llm; **replaces `ClaudeAgentOrchestrator`
  with a LangChain4j `AiServices` tool-use loop**; migrates `node.tools()` to LangChain4j tools (impls
  reused); tier→model from settings; drops Anthropic SDK.

## 3. Dependency graph (build order)

```
T1 platform-llm + analysis (platform-ai)
        │  (module + client + settings keys settle here)
        ├────────────► T2 settings UI (portal)
        ├────────────► T3 platform-agent ports to LiteLlmClient   ← highest risk
        │                       │
        ▼                       ▼
T4 export snippets (UI)   T5 cleanup + docs + security (last)
```

- T1 is the foundation **and** the first working vertical path (analysis through LiteLLM).
- T2 and T3 both consume T1's module + settings keys; can run in parallel after CP-A but T3 gets its own gate.
- T4 needs T2's configured model list. T5 is cross-cutting cleanup, last.

## 4. Why vertical, not horizontal
Each slice is a complete path: S1 "analysis goes through LiteLLM", S2 "admin configures it", S3 "an
agent runs on it", S4 "a dev exports config". No half-built 'module created but nothing uses it' state —
T1 ends with platform-ai actually analysing via the new module.

## 5. Phases & checkpoints

| Phase | Tasks | Checkpoint (human gate) |
|---|---|---|
| **P1 Foundation + analysis** | T1 | **CP-A:** `platform-llm` builds; platform-ai `analyse()` + `/test` work against a real LiteLLM endpoint; old clients/SDK gone from platform-ai |
| **P2 Admin UI** | T2 | **CP-B:** settings round-trip in `/settings/ai`; keys masked; test button works |
| **P3 Agents** | T3a (spike), T3b (migrate) | **CP-C (critical):** an agent flow runs via LangChain4j→LiteLLM; tool-use + prompt caching + token accounting validated (SPEC F7 checklist); fallback verified; Anthropic SDK removed |
| **P4 Exports + cleanup** | T4, T5 | **CP-D:** snippets valid for all 3 tools; docs/module-map updated; `mvn -Pformat` clean; Trivy 0 HIGH/CRITICAL |

Stop at each checkpoint for review.

## 6. Risks & mitigations
- **R1 (high): agent capability via LangChain4j + LiteLLM.** Spike in T3 must validate the capability
  checklist (SPEC F7): tool calling (multi/parallel), **prompt caching** of the big system prompt
  (today `CacheControlEphemeral`), **cache-aware token accounting**, the ≤25-iteration cap, structured
  output. If caching can't pass through, accept higher cost OR use Option B (Anthropic SDK → LiteLLM
  Anthropic endpoint) for cached flows. Decide at CP-C.
- **R2: tool migration.** `node.tools()` returns Anthropic `Tool` objects; must re-express as LangChain4j
  tools while reusing the impls (`PlatformQueryTools`, etc.). Do one node first (automation-gen) end-to-end.
- **R3: LangChain4j ↔ Spring Boot 4.** Use LangChain4j as a plain library (no spring-boot-starter); wire
  beans manually to avoid version coupling. Confirm versions resolve at T1.
- **R4: cross-module move.** Moving `AiClient`/`AiAnalysisResponse` to `platform-llm` touches platform-ai
  imports — compile early; keep package path stable (`com.platform.llm`).
- **R5: secrets.** Reuse credential encryption; never log/return keys.
- **R6: external tool config drift.** Verify OpenCode/Claude-Router/VS Code field names against current docs in T4.

## 7. Out of scope (per SPEC)
LiteLLM container in compose; cost/quota dashboards. (Anthropic SDK is *removed*, except the optional
fallback path in platform-llm. Agent loop is LangChain4j `AiServices`, not custom.)

## 8. Definition of done
All AC met (incl. SPEC F7 capability checklist); `mvn -Pformat` clean;
`mvn -pl platform-llm,platform-ai,platform-agent,platform-core test` green; frontend `tsc -b` + `vite build`
green; Trivy HIGH/CRITICAL = 0; one LiteLLM gateway + LangChain4j agent layer across the platform.
