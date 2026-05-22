# System Architecture — Current State (v2.1)

> v2.0 (b4640398780f0f339bb3a9bf5e83b271ab4e1b01) through HEAD.
> Diagrams use [Mermaid](https://mermaid.js.org/) — rendered natively in GitHub, GitLab, and VS Code (Markdown Preview Enhanced).

---

## 1. System Context

```mermaid
flowchart LR
    subgraph CLIENTS["External Clients"]
        direction TB
        CI["CI/CD Pipelines<br/>(GH Actions · GitLab · Jenkins)"]
        SDK_J["Java SDK<br/>(JUnit5 / TestNG / Cucumber / K6)"]
        SDK_JS["JS SDK<br/>(@platform/playwright-reporter)"]
        BROWSER["Browser (React SPA)"]
    end

    subgraph PLATFORM["Platform — Application Layer"]
        direction TB
        ING["platform-ingestion :8081<br/>Parsers · Coverage · TCM · API-key auth"]
        PORTAL["platform-portal :8085<br/>BFF + React 19 SPA"]
        AGENT["platform-agent :8086<br/>AI Workflow Hub"]
        ANALYTICS["platform-analytics :8082<br/>Flakiness · TIA · Quality gates · Alerts"]
        AI["platform-ai :8084<br/>Failure classification · Nightly batch"]
        INT["platform-integration :8083<br/>Issue lifecycle (JIRA/Linear/GitHub)"]
    end

    subgraph INFRA["Message Bus + Data Layer"]
        direction TB
        subgraph KAFKA["Apache Kafka 4.2.0 (KRaft)"]
            direction LR
            K1["test.results.raw"]
            K2["test.results.analyzed"]
            K3["test.flakiness.events"]
            K4["test.alert.events"]
            K5["test.integration.commands"]
            K6["agent.workflow.events<br/>agent.approval.requests<br/>agent.approval.decisions"]
        end
        subgraph DATA["Data Layer"]
            direction LR
            PG[("PostgreSQL 17 :5432<br/>Flyway V1–V45+")]
            OS[("OpenSearch 3.5.0 :9200<br/>k-NN similarity search")]
            RD[("Redis 8.6.1 :6379<br/>Cache · Rate-limit · Dedup")]
            MN[("MinIO :9000<br/>Artifacts · Diffs<br/>Knowledge · Checkpoints")]
        end
    end

    subgraph EXTSVCS["External Services"]
        direction TB
        JIRA["JIRA / Linear"]
        GHAPI["GitHub API"]
        CLAUDE["Claude API<br/>claude-sonnet-4-6"]
    end

    CI & SDK_J & SDK_JS -->|POST /api/v1/results/ingest| ING
    BROWSER --> PORTAL

    ING -->|publish| K1
    K1 -->|consume| ANALYTICS & AI
    ANALYTICS -->|publish| K3 & K4
    AI -->|publish| K2
    K2 & K3 & K4 & K5 -->|consume| INT
    AGENT -->|publish/consume| K6

    PORTAL -->|proxy REST| ING & ANALYTICS & AI & AGENT

    ING & ANALYTICS & AI & INT & AGENT & PORTAL --> PG
    AI --> OS
    AGENT --> MN

    INT --> JIRA
    AGENT --> GHAPI
    AI & AGENT --> CLAUDE
```

---

## 2. Agent Grid — High-Level Architecture

> Inspired by Selenium Grid: **Hub** = source-of-truth controller + task router; **Nodes** = stateless Claude-powered workers that register their capabilities and accept sessions.

```mermaid
flowchart LR
    subgraph TRIGGERS["Inbound Triggers"]
        direction TB
        GHW["GitHub Webhook<br/>PR open · sync · close"]
        JIRAW["JIRA Webhook<br/>issue update"]
        LINW["Linear Webhook"]
        SLACKI["Slack Interactions<br/>Block Kit approve/reject"]
        PORTALT["Portal<br/>manual trigger"]
        SCHED["Scheduler<br/>nightly digest · polling fallback"]
    end

    subgraph HUB["HUB — platform-agent :8086  (Source of Truth + Coordinator)"]
        direction TB

        subgraph SYNC["Source Sync Layer"]
            RSS["RequirementSyncService<br/>JIRA / Linear → platform_requirements<br/>(JIRA/LinearWebhookController + polling)"]
            CRI["CodeRepoIndex<br/>GitHub PR diffs → MinIO platform-diffs<br/>(GitHubWebhookController + polling)"]
        end

        subgraph GRAPH["Knowledge Graph"]
            GS["GraphService<br/>Requirement graph traversal<br/>(parent · linked · covered-by edges)"]
            RCP["RequirementChangeProcessor<br/>AC-level diff → NEEDS_UPDATE / OBSOLETE"]
            TPG["TestPlanGeneratorService<br/>Release scope → test plan + coverage gaps"]
        end

        subgraph CORE["Hub Core"]
            CTX["ContextAssembler<br/>Builds 5-tier ContextBundle<br/>(Req · TestCase · AutoTest · Execution · Monitor)"]
            ROUTER["CapabilityTaskRouter<br/>Matches task.requiredCapabilities<br/>to registered nodes (+ LLM tier filter)"]
            REG["NodeRegistry<br/>InMemory ConcurrentHashMap<br/>70 s heartbeat eviction"]
            WFS["AgentWorkflowService<br/>Workflow FSM · step loop<br/>review-pause · Kafka events"]
            TBG["TokenBudgetGuard<br/>Monthly per-project budget cap<br/>hard-stop before Claude call"]
        end

        subgraph REVIEW["Review Gateway"]
            RGW["KafkaReviewGateway<br/>persists AgentReviewRequest<br/>emits agent.approval.requests"]
            SLACK_N["SlackNotificationService<br/>Block Kit interactive messages"]
            PORTAL_Q["Portal Work-Items<br/>/api/portal/projects/{id}/work-items"]
        end
    end

    subgraph PROTOCOL["Hub ↔ Node Session Protocol"]
        direction TB
        P1["Node → Hub<br/>POST /hub/nodes/register<br/>(capabilities + endpoint + heartbeat)"]
        P2["Hub → Node<br/>POST /node/sessions<br/>(ContextBundle + credentials)"]
        P3["Node → Hub (live)<br/>POST /hub/sessions/{id}/step<br/>POST /hub/sessions/{id}/artifact<br/>POST /hub/sessions/{id}/complete|fail"]
    end

    subgraph NODES["NODE POOL — stateless, horizontally scalable"]
        direction TB

        subgraph NODES_ANALYSIS["Analysis & Insight"]
            ANA["AnalysisNode<br/>ANALYSE_PR_IMPACT<br/>PR diff + TIA → coverage gap<br/>Posts PR comment<br/>sonnet-4-6"]
            INS["InsightNode<br/>GENERATE_INSIGHT_DIGEST<br/>Trends + flakiness → Slack digest<br/>sonnet-4-6"]
        end

        subgraph NODES_REQUIREMENTS["Requirements"]
            EAC["ExtractAcceptanceCriteriaNode<br/>EXTRACT_ACCEPTANCE_CRITERIA<br/>Raw req → structured ACs<br/>sonnet-4-6"]
        end

        subgraph NODES_TESTGEN["Test Generation"]
            TCG["TestCaseGenerationNode<br/>GENERATE_TEST_CASES<br/>ACs + dedup context → ManagedTestCase rows<br/>sonnet-4-6"]
            TGN["TestGenNode<br/>GENERATE_TESTS_QUICK<br/>Lightweight path<br/>sonnet-4-6"]
        end

        subgraph NODES_AUTOMATION["Automation"]
            ACG["AutomationCodeGenerationNode<br/>GENERATE_AUTOMATION<br/>TestCase + linked reqs → Playwright/JUnit5 PR<br/>opus-4-7"]
            HEAL["HealingNode<br/>HEAL_FAILING_TEST<br/>Failure + test code → patch PR<br/>opus-4-7"]
        end
    end

    subgraph ORCH["Agent Orchestrator (embedded in each node)"]
        direction TB
        CLO["ClaudeAgentOrchestrator<br/>MAX_TOOL_ITERATIONS = 25<br/>Full tool-use loop · __AWAITING_REVIEW__ sentinel<br/>Model selected by LlmTier"]
        SMRZ["StepSummarizer<br/>haiku-4-5 compresses tool results<br/>8 K token diff → ~42 token summary"]
        CKPT["RedisCheckpointService<br/>TTL by ResumeStrategy<br/>prompt-cache ≤5 min · compressed ≤24 h · handoff >24 h"]
    end

    subgraph ARTIFACTS["Artifact Targets"]
        direction TB
        GH_PR["GitHub Pull Request<br/>(automation code · fixes)"]
        JIRA_T["JIRA / Linear Ticket<br/>(created · updated · closed)"]
        PG_ARTS["PostgreSQL<br/>(test cases · analyses · test plans)"]
        MN_ARTS["MinIO<br/>(diffs · checkpoints · knowledge)"]
        SLACK_T["Slack Channel<br/>(digest · approval request)"]
    end

    TRIGGERS --> HUB
    SYNC --> GRAPH
    GRAPH --> CTX
    CTX --> ROUTER
    ROUTER --> REG
    WFS --> ROUTER

    HUB --> PROTOCOL
    PROTOCOL --> NODES
    NODES --> ORCH
    ORCH --> ARTIFACTS
    ORCH --> RGW
    RGW --> SLACK_N & PORTAL_Q

    SLACKI --> SLACKI_H(["SlackInteractionController"])
    SLACKI_H --> RGW

    classDef hub fill:#dbeafe,stroke:#3b82f6,color:#1e3a5f
    classDef node fill:#dcfce7,stroke:#22c55e,color:#14532d
    classDef trigger fill:#fef9c3,stroke:#eab308,color:#713f12
    classDef artifact fill:#f3e8ff,stroke:#a855f7,color:#3b0764
    classDef orch fill:#ffedd5,stroke:#f97316,color:#7c2d12

    class HUB,CORE,SYNC,GRAPH,REVIEW hub
    class NODES,NODES_ANALYSIS,NODES_REQUIREMENTS,NODES_TESTGEN,NODES_AUTOMATION node
    class TRIGGERS trigger
    class ARTIFACTS artifact
    class ORCH orch
```

### Node Capability Matrix

| Node | Task Type | LLM Tier | Tools | Output |
|---|---|---|---|---|
| `ExtractAcceptanceCriteriaNode` | `EXTRACT_ACCEPTANCE_CRITERIA` | sonnet-4-6 | `store_acceptance_criteria`, `request_review` | Structured ACs in DB |
| `TestCaseGenerationNode` | `GENERATE_TEST_CASES` | sonnet-4-6 | `platform_query`, `store_test_case` | `ManagedTestCase` rows |
| `TestGenNode` | `GENERATE_TESTS_QUICK` | sonnet-4-6 | `platform_query`, `store_test_case` | `ManagedTestCase` rows |
| `AnalysisNode` | `ANALYSE_PR_IMPACT` | sonnet-4-6 | `github_get_pr_diff`, `platform_get_tia_impact`, `github_post_pr_comment` | PR comment + `ImpactAnalysis` |
| `AutomationCodeGenerationNode` | `GENERATE_AUTOMATION` | opus-4-7 | `platform_query`, `github_create_pr` | GitHub PR with test code |
| `HealingNode` | `HEAL_FAILING_TEST` | opus-4-7 | `github_read_file`, `github_commit_file`, `github_create_pr` | Fix PR |
| `InsightNode` | `GENERATE_INSIGHT_DIGEST` | sonnet-4-6 | `platform_get_trends`, `platform_get_flakiness_leaderboard` | Slack digest |

---

## 3. Agent Hub — Internal Detail

```mermaid
flowchart LR
    subgraph TRIGGERS["Inbound Triggers"]
        direction TB
        WC["WorkflowController<br/>REST /api/agent/workflows"]
        GHW["GitHubWebhookController<br/>PR opened / sync / closed"]
        GHS["GitHubSyncController<br/>Manual sync"]
        GPS["GitHubPollingService<br/>Scheduled fallback poll"]
        NRC["NodeRegistrationController<br/>Node self-registration"]
    end

    subgraph HUB["Agent Hub Core"]
        direction TB
        ROUTER["CapabilityTaskRouter<br/>Routes by capability type"]
        REGISTRY["NodeRegistry<br/>(InMemoryNodeRegistry)<br/>LocalNodeRegistrar seeds locals"]
        CTX["ContextAssembler<br/>(DefaultContextAssembler)<br/>Fetches Req · TestCase · PR diff"]
    end

    subgraph NODES["Agent Nodes (implement AgentNode)"]
        direction TB
        TCG["TestCaseGenerationNode<br/>Req → test cases<br/>Links req IDs, dedup context"]
        ACG["AutomationCodeGenerationNode<br/>TestCase + linked reqs → PR code<br/>Opens GitHub PR"]
        TGN["TestGenNode<br/>Lightweight gen path"]
        ANA["AnalysisNode<br/>Failure root-cause triage"]
        HEAL["HealingNode<br/>Flaky fix · PR generation"]
        INS["InsightNode<br/>Cross-project quality narrative"]
        EAC["ExtractAcceptanceCriteriaNode<br/>Parses AC from requirement"]
    end

    subgraph REVIEW["Review Gateway"]
        direction TB
        RGW["KafkaReviewGateway<br/>emits agent.approval.requests<br/>consumes agent.approval.decisions"]
    end

    subgraph IA["Impact Analysis (in platform-ingestion)"]
        direction TB
        IAS["ImpactAnalysisService<br/>PR diffs + Reqs + existing TCs<br/>→ Claude API<br/>→ UPDATE / CREATE suggestions"]
    end

    WC & GHW & GHS & GPS & NRC --> ROUTER
    ROUTER --> REGISTRY
    ROUTER --> CTX
    CTX --> NODES
    NODES --> REVIEW
    REVIEW -->|"agent.approval.requests"| KAFKA_OUT(["Kafka"])
    KAFKA_OUT -->|"agent.approval.decisions"| REVIEW

    GHW & WC --> IAS
```

---

## 4. Test Execution Event Flow

```mermaid
sequenceDiagram
    participant CI as CI/CD / SDK
    participant ING as platform-ingestion
    participant K as Kafka
    participant ANA as platform-analytics
    participant AI as platform-ai
    participant INT as platform-integration
    participant PG as PostgreSQL
    participant OS as OpenSearch

    CI->>ING: POST /api/v1/results/ingest<br/>(JUnit XML / Cucumber / Playwright / K6 …)
    ING->>ING: Parse → UnifiedTestResult
    ING->>PG: persist test_executions + test_case_results
    ING->>PG: upsert test_coverage_mappings
    ING->>K: publish test.results.raw

    par Analytics consumer
        K->>ANA: consume test.results.raw
        ANA->>PG: recompute flakiness_snapshots
        ANA->>K: publish test.flakiness.events
        ANA->>K: publish test.alert.events
    and AI consumer
        K->>AI: consume test.results.raw
        AI->>AI: classify failures via Claude API
        AI->>PG: persist failure_analyses
        AI->>OS: index for k-NN similarity
        AI->>K: publish test.results.analyzed
    end

    K->>INT: consume test.results.analyzed + test.flakiness.events
    INT->>INT: IssueDecisionEngine evaluates rules
    INT-->>JIRA: create / update / close ticket
```

---

## 5. AI-Assisted Test Case Lifecycle

```mermaid
sequenceDiagram
    participant USER as Portal User
    participant PORTAL as platform-portal
    participant AGENT as platform-agent
    participant CLAUDE as Claude API
    participant PG as PostgreSQL
    participant GH as GitHub API
    participant RQ as Review Queue

    Note over USER,RQ: Generate test cases from requirements
    USER->>PORTAL: "Generate Test Cases from AI"
    PORTAL->>AGENT: POST /api/agent/workflows
    AGENT->>PG: load requirements + existing test cases (dedup)
    AGENT->>CLAUDE: TestCaseGenerationNode prompt
    CLAUDE-->>AGENT: generated test cases (JSON)
    AGENT->>PG: persist ManagedTestCase<br/>sourceReqId · linkedReqIds · updatedBy=AI · status=DRAFT
    AGENT->>RQ: emit agent.approval.requests

    Note over USER,RQ: Human review → approve
    USER->>PORTAL: ReviewQueuePage → Approve
    PORTAL->>AGENT: POST /work-items/{id}/approve

    Note over USER,GH: Trigger automation code generation
    USER->>PORTAL: "Generate Automation"
    PORTAL->>AGENT: POST /{tcId}/generate-automation
    AGENT->>PG: load TestCase + linkedRequirementIds → fetch full Req text
    AGENT->>CLAUDE: AutomationCodeGenerationNode prompt
    CLAUDE-->>AGENT: Playwright / JUnit5 code
    AGENT->>GH: open Pull Request
    AGENT->>PG: update automationPrUrl · automationWorkflowId · status=IN_PROGRESS

    Note over USER,PG: Impact analysis applies a suggestion
    USER->>PORTAL: ImpactAnalysesPage → "Apply to linked test case"
    PORTAL->>AGENT: POST /{tcId}/apply-suggestion
    AGENT->>PG: update TestCase fields<br/>updatedBy=IMPACT_ANALYSIS · lastUpdatedByAnalysisId · status=UNDER_REVIEW
```

---

## 6. Test Case State Machine

```mermaid
stateDiagram-v2
    [*] --> DRAFT : created (human or AI)

    DRAFT --> UNDER_REVIEW : submit for review
    DRAFT --> APPROVED : direct approve
    DRAFT --> REJECTED : reject

    UNDER_REVIEW --> APPROVED : approve
    UNDER_REVIEW --> REJECTED : reject

    APPROVED --> UNDER_REVIEW : impact analysis applies suggestion

    APPROVED --> AutomationInProgress : trigger automation generation
    state AutomationInProgress {
        [*] --> IN_PROGRESS : automationWorkflowId + automationPrUrl set
        IN_PROGRESS --> DONE : PR merged
        IN_PROGRESS --> FAILED : workflow error
    }

    REJECTED --> DRAFT : re-open / edit
```

---

## 7. Test Case Linkage Model

```mermaid
erDiagram
    REQUIREMENT {
        uuid id
        string externalId
        string title
        string description
        jsonb acceptanceCriteria
        string issueType
        string status
        uuid parentId
    }

    MANAGED_TEST_CASE {
        uuid id
        uuid sourceRequirementId
        jsonb linkedRequirementIds
        uuid automationWorkflowId
        uuid lastUpdatedByAnalysisId
        string automationPrUrl
        string status
        string coverageStatus
        string automationStatus
        string updatedBy
        jsonb steps
        jsonb acRefs
    }

    IMPACT_ANALYSIS {
        uuid id
        string name
        string status
        jsonb linkedPrs
        jsonb linkedRequirementIds
        jsonb suggestions
    }

    AGENT_WORKFLOW {
        uuid id
        string workflowType
        string status
        jsonb inputPayload
    }

    GITHUB_PR {
        string url
        int number
        string repoFullName
        string status
    }

    REQUIREMENT ||--o{ MANAGED_TEST_CASE : "sourceRequirementId (origin)"
    REQUIREMENT }o--o{ MANAGED_TEST_CASE : "linkedRequirementIds[] (many-to-many)"
    REQUIREMENT }o--o{ IMPACT_ANALYSIS : "linkedRequirementIds[]"
    MANAGED_TEST_CASE }o--o| IMPACT_ANALYSIS : "lastUpdatedByAnalysisId"
    MANAGED_TEST_CASE }o--o| AGENT_WORKFLOW : "automationWorkflowId"
    AGENT_WORKFLOW ||--o| GITHUB_PR : "opens via GitHub API"
```

---

## 8. Portal — Page Map

```mermaid
flowchart LR
    subgraph NAV["Navigation"]
        direction TB
        OO["OrgOverview"]
        PD["ProjectDetail"]
        REQ["RequirementsPage<br/>(tree view default + search)"]
        IA["ImpactAnalysesPage"]
        PRA["PRAnalysesPage"]
        TC["TestCasesPage<br/>(CRUD + requirement linking)"]
        TR["TestRunsPage"]
        TRE["TestRunExecutionPage"]
        RQ["ReviewQueuePage<br/>(approve/reject AI work)"]
        FT["FlakyTestsPage"]
        AL["AlertsPage"]
        AK["ApiKeysPage"]
        AI["AiSettingsPage"]
        PS["ProjectSettingsPage"]
    end

    subgraph BFF["BFF Endpoints  /api/portal/…"]
        direction TB
        E1["/overview · /teams · /projects"]
        E2["/projects/{id} · /flakiness · /quality-gate<br/>/pass-rate-trend · /executions · /impact/summary"]
        E3["/requirements · /requirements/stats · /requirements/{id}"]
        E4["/impact-analyses · /impact-analyses/{id}<br/>/impact-analyses/prs"]
        E5["/pr-analyses"]
        E6["/test-cases · /test-cases/{id}<br/>/{tcId}/link-requirement · /apply-suggestion<br/>/{tcId}/submit-review · /approve · /reject<br/>/{tcId}/generate-automation · /test-suites"]
        E7["/test-runs · /test-runs/{id}"]
        E8["/test-runs/{id}/executions"]
        E9["/work-items · /review-requests/{id}/approve|reject"]
        E10["/flakiness · /flakiness/recompute · /flakiness/fix"]
        E11["/alerts"]
        E12["/api-keys"]
        E13["/ai/settings · /ai/settings/test · /ai/analyse/run-now"]
        E14["/integrations · /integrations/sync · /projects/{id}"]
    end

    OO --> E1
    PD --> E2
    REQ --> E3
    IA --> E4
    PRA --> E5
    TC --> E6
    TR --> E7
    TRE --> E8
    RQ --> E9
    FT --> E10
    AL --> E11
    AK --> E12
    AI --> E13
    PS --> E14
```

---

## 9. Observability Stack

```mermaid
flowchart LR
    subgraph SERVICES["All Platform Services :8081–8086"]
        ACT["/actuator/health<br/>/actuator/metrics<br/>/actuator/prometheus"]
    end

    subgraph METRICS["Metrics Pipeline"]
        PROM["Prometheus v3.10.0<br/>:9090<br/>scrapes all services"]
        GRAFANA["Grafana 12.4.0<br/>:3000<br/>Dashboards"]
        INFLUX["InfluxDB 1.8<br/>:8086<br/>k6 / perf metrics"]
        AM["AlertManager<br/>Prometheus rules → alerts"]
    end

    subgraph LOGS["Log Pipeline"]
        STDOUT["stdout (JSON structured)"]
        PT["Promtail<br/>collects container logs"]
        LOKI["Loki 3.5.0<br/>:3100<br/>log aggregation"]
        LS["Logstash<br/>:5044<br/>OpenSearch indexing"]
        OS["OpenSearch 3.5.0<br/>:9200"]
    end

    subgraph TRACES["Trace Pipeline"]
        OTEL["OpenTelemetry SDK<br/>All services instrumented"]
        JAEGER["Jaeger<br/>:16686<br/>OTLP gRPC :4317"]
    end

    SERVICES --> ACT
    ACT --> PROM
    PROM --> GRAFANA
    PROM --> AM
    INFLUX --> GRAFANA

    SERVICES --> STDOUT
    STDOUT --> PT
    PT --> LOKI
    LOKI --> GRAFANA
    STDOUT --> LS
    LS --> OS

    SERVICES --> OTEL
    OTEL --> JAEGER

    subgraph DASHBOARDS["Grafana Dashboards"]
        D1["Platform Overview<br/>pass-rate · run volume · flakiness"]
        D2["Flakiness<br/>CRITICAL_FLAKY heatmap · score dist"]
        D3["AI Analysis<br/>classification rate · token usage"]
        D4["Performance Signals<br/>p50/p90/p95/p99 · error rate · VUs"]
        D5["Agent Workflows<br/>pending · running · approval queue"]
    end

    GRAFANA --> DASHBOARDS
```

---

## 10. Deployment Topology

```mermaid
flowchart LR
    subgraph LOCAL["Local Dev — Docker Compose"]
        direction TB
        subgraph APP_LOCAL["Application Containers"]
            direction LR
            ING_L["ingestion :8081"]
            ANA_L["analytics :8082"]
            INT_L["integration :8083"]
            AI_L["ai :8084"]
            POR_L["portal :8085"]
            AGT_L["agent :8086/8087"]
        end
        subgraph INFRA_LOCAL["Infrastructure Containers"]
            direction LR
            PG_L["postgres :5432"]
            KF_L["kafka :9092"]
            RD_L["redis :6379"]
            OS_L["opensearch :9200"]
            MN_L["minio :9000"]
            GF_L["grafana :3000"]
            PR_L["prometheus :9090"]
            LK_L["loki :3100"]
            JG_L["jaeger :16686"]
            IDB_L["influxdb :8086"]
        end
    end

    subgraph K8S["Production — Kubernetes + Helm"]
        direction TB
        subgraph WORKLOADS["Deployments + HPA"]
            direction LR
            ING_K["ingestion"]
            ANA_K["analytics"]
            INT_K["integration"]
            AI_K["ai"]
            POR_K["portal"]
            AGT_K["agent"]
        end
        subgraph STATEFUL["StatefulSets"]
            direction LR
            PG_K["PostgreSQL<br/>(or managed RDS)"]
            KF_K["Kafka KRaft<br/>3 controllers + 3 brokers"]
            RD_K["Redis<br/>(or ElastiCache)"]
            OS_K["OpenSearch<br/>(or managed)"]
            MN_K["MinIO<br/>(or S3)"]
        end
        subgraph OBS_K["Observability"]
            direction LR
            KPS["kube-prometheus-stack"]
            LOKI_K["Loki"]
            JAEGER_K["Jaeger operator"]
        end
    end
```

---

## 11. LLM Provider Flexibility & Self-Hosted Models

> Replacing cloud LLM calls with a self-hosted model is the primary lever for reducing per-token costs.
> The platform has a provider-abstraction layer already in place for failure classification (`platform-ai`);
> the agent orchestrator (`platform-agent`) currently uses the Anthropic SDK directly and needs an additional path.

### 11.1 Current Provider Abstraction (`platform-ai`)

```mermaid
flowchart LR
    subgraph SETTINGS["AiSettings  (portal → /api/portal/ai/settings)"]
        P["provider:<br/>'anthropic' | 'openai'"]
        M["model: string<br/>e.g. gpt-4o · llama3.3 · mistral"]
        K["apiKey (encrypted in platform_settings)"]
        BU["baseUrl ⚠ not yet in UI<br/>DB key: ai.openai.base-url<br/>e.g. http://localhost:11434/v1"]
    end

    subgraph ROUTER["AiClientRouter  (@Primary bean — no restart needed)"]
        R["reads ai.provider from DB at call time<br/>routes to active client"]
    end

    subgraph ANTHROPIC_PATH["Anthropic path"]
        CC["ClaudeApiClient<br/>Anthropic Java SDK<br/>claude-sonnet-4-6 (default)"]
    end

    subgraph OPENAI_PATH["OpenAI-compatible path"]
        OC["OpenAiClient<br/>Spring RestClient<br/>POST {baseUrl}/v1/chat/completions<br/>Bearer {apiKey}"]
        subgraph LOCAL["Self-Hosted (OpenAI-compatible API)"]
            OL["Ollama<br/>localhost:11434/v1<br/>Llama 3.3 · Mistral · Gemma · DeepSeek · Phi"]
            VL["vLLM<br/>Production inference<br/>OpenAI-compatible /v1"]
            LM["LM Studio<br/>Desktop app<br/>OpenAI-compatible endpoint"]
            LA["LocalAI<br/>Drop-in replacement<br/>CPU / GPU inference"]
        end
        subgraph CLOUD_COMPAT["Cloud OpenAI-compatible"]
            OAI["api.openai.com<br/>gpt-4o · gpt-4o-mini"]
            GRQ["api.groq.com/openai/v1<br/>llama3-70b · mixtral (hosted fast)"]
            TGT["api.together.xyz/v1<br/>Open models, pay-per-token"]
        end
    end

    SETTINGS --> ROUTER
    ROUTER -->|"provider=anthropic"| CC
    ROUTER -->|"provider=openai"| OC
    OC --> LOCAL
    OC --> CLOUD_COMPAT
```

**What works today** — change `ai.provider` to `openai` in AI Settings and point `ai.openai.base-url` directly in `platform_settings` (DB) to any OpenAI-compatible server. No service restart needed.

**Remaining gap** — `AiSettings` UI and `AiSettingsUpdate` DTO do not yet expose a `baseUrl` field; it must be set directly in the database until the portal form is extended.

---

### 11.2 Agent Orchestrator (`platform-agent`)

```mermaid
flowchart LR
    subgraph ORCH["ClaudeAgentOrchestrator — Current"]
        direction TB
        TIER["LlmTier from ContextBundle<br/>(set per AgentNode)"]
        RES["resolveModel(tier)<br/>COMPLEX  → claude-opus-4-6<br/>default  → claude-sonnet-4-6"]
        SDK["Anthropic Java SDK<br/>AnthropicOkHttpClient"]
    end

    subgraph FUTURE["Planned Extension — OpenAI-compatible path"]
        direction TB
        OAS["OpenAI-compatible AgentOrchestrator<br/>reuses same AgentNode interface<br/>baseUrl = local LLM endpoint<br/>no tool-calling format change needed<br/>(OpenAI tool_use schema ≡ Anthropic tool_use schema)"]
    end

    TIER --> RES --> SDK
    SDK -->|"today"| ANTHROPIC["Anthropic Claude API"]
    OAS -->|"future"| LOCAL_AGENT["Ollama / vLLM<br/>with tool-calling support<br/>(llama3.3-70b, Mistral Large, Qwen2.5-72b)"]
```

**Current state** — `ClaudeAgentOrchestrator` exclusively uses the Anthropic SDK.
Extending it requires implementing a second orchestrator that speaks the OpenAI Chat Completions format, then selecting it via `LlmTier` or a new `ai.agent.provider` setting.

> Not all local models support tool use reliably. Models known to work: **Llama 3.3 70B**, **Mistral Large 2**, **Qwen 2.5 72B**, **DeepSeek-R1 32B+**.

---

### 11.3 Cost Tier Mapping

| Task | Node | Current Model | Local Alternative | Tool Use Required | Notes |
|---|---|---|---|---|---|
| AC extraction | `ExtractAcceptanceCriteriaNode` | sonnet-4-6 | Mistral 7B · Llama 3.2 3B | Yes (simple) | Structured output, low complexity |
| Test case generation | `TestCaseGenerationNode` | sonnet-4-6 | Llama 3.3 70B · Mistral Large | Yes | Quality matters — use ≥ 30B param model |
| PR impact analysis | `AnalysisNode` | sonnet-4-6 | Llama 3.3 70B · Qwen 2.5 72B | Yes | Needs code reasoning |
| Insight digest | `InsightNode` | sonnet-4-6 | Mistral 7B · Gemma 3 12B | Yes | Narrative generation, tolerates degradation |
| Automation code gen | `AutomationCodeGenerationNode` | opus-4-7 | Llama 3.3 70B · Qwen 2.5 72B | Yes | Complex coding; test output quality before switching |
| Test healing | `HealingNode` | opus-4-7 | Llama 3.3 70B · DeepSeek-R1 32B | Yes | Complex reasoning; test output quality before switching |
| Step summarisation | `StepSummarizer` | haiku-4-5 | Gemma 3 1B · Phi-4 Mini | No | Pure text compression, any fast model works |
| Failure classification | `FailureClassificationService` | sonnet-4-6 | Mistral 7B · Llama 3.2 3B | No | Via `AiClientRouter`; works today with `provider=openai` |

---

### 11.4 Recommended Local Setup (Ollama)

```mermaid
flowchart LR
    subgraph DOCKER["docker-compose.yml addition"]
        OLL["ollama<br/>image: ollama/ollama<br/>ports: 11434:11434<br/>volumes: ollama_data:/root/.ollama<br/>gpus: all  (optional)"]
    end

    subgraph PLATFORM_SETTINGS["platform_settings rows (Flyway or portal UI)"]
        S1["ai.provider = openai"]
        S2["ai.openai.base-url = http://ollama:11434/v1"]
        S3["ai.openai.api-key = ollama  (any non-empty string)"]
        S4["ai.model = llama3.3  (or mistral-large · qwen2.5)"]
    end

    subgraph PULL["Pull models once"]
        PL["docker exec platform-ollama<br/>ollama pull llama3.3<br/>ollama pull mistral-large<br/>ollama pull phi4-mini"]
    end

    DOCKER --> PLATFORM_SETTINGS --> PULL
```

**Cost comparison (approximate, 1 M tokens):**

| Provider | Input | Output |
|---|---|---|
| Claude sonnet-4-6 | $0.30 | $1.50 |
| Claude opus-4-7 | $1.50 | $7.50 |
| OpenAI gpt-4o | $2.50 | $10.00 |
| Ollama (self-hosted GPU) | ~$0.00 | ~$0.00 |
| Groq (hosted, llama3-70b) | $0.059 | $0.079 |

---

*Last updated: v2.1 — test case linkage tracking (V45 migration), ReviewQueue page, impact analysis apply-suggestion flow, requirements tree-view default with search, LLM provider flexibility.*
