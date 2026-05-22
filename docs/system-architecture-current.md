# System Architecture — Current State (v2.1)

> v2.0 (b4640398780f0f339bb3a9bf5e83b271ab4e1b01) through HEAD.
> Diagrams use [Mermaid](https://mermaid.js.org/) — rendered natively in GitHub, GitLab, and VS Code (Markdown Preview Enhanced).

---

## 1. System Context

```mermaid
flowchart TD
    subgraph CLIENTS["External Clients"]
        direction LR
        CI["CI/CD Pipelines\n(GH Actions · GitLab · Jenkins)"]
        SDK_J["Java SDK\n(JUnit5 / TestNG / Cucumber / K6)"]
        SDK_JS["JS SDK\n(@platform/playwright-reporter)"]
        BROWSER["Browser (React SPA)"]
    end

    subgraph EXTSVCS["External Services"]
        direction LR
        JIRA["JIRA / Linear"]
        GHAPI["GitHub API"]
        CLAUDE["Claude API\nclaude-sonnet-4-6"]
    end

    subgraph PLATFORM["Platform — Application Layer"]
        direction LR
        ING["platform-ingestion :8081\nParsers · Coverage · TCM · API-key auth"]
        PORTAL["platform-portal :8085\nBFF + React 19 SPA"]
        AGENT["platform-agent :8086\nAI Workflow Hub"]
        ANALYTICS["platform-analytics :8082\nFlakiness · TIA · Quality gates · Alerts"]
        AI["platform-ai :8084\nFailure classification · Nightly batch"]
        INT["platform-integration :8083\nIssue lifecycle (JIRA/Linear/GitHub)"]
    end

    subgraph KAFKA["Apache Kafka 4.2.0 (KRaft) — Message Bus"]
        direction LR
        K1["test.results.raw"]
        K2["test.results.analyzed"]
        K3["test.flakiness.events"]
        K4["test.alert.events"]
        K5["test.integration.commands"]
        K6["agent.workflow.events\nagent.approval.requests\nagent.approval.decisions"]
    end

    subgraph DATA["Data Layer"]
        direction LR
        PG[("PostgreSQL 17 :5432\nFlyway V1–V45+")]
        OS[("OpenSearch 3.5.0 :9200\nk-NN similarity search")]
        RD[("Redis 8.6.1 :6379\nCache · Rate-limit · Dedup")]
        MN[("MinIO :9000\nArtifacts · Diffs\nKnowledge · Checkpoints")]
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

## 2. Agent Hub — Internal Architecture

```mermaid
flowchart TD
    subgraph TRIGGERS["Inbound Triggers"]
        WC["WorkflowController\nREST /api/agent/workflows"]
        GHW["GitHubWebhookController\nPR opened / sync / closed"]
        GHS["GitHubSyncController\nManual sync"]
        GPS["GitHubPollingService\nScheduled fallback poll"]
        NRC["NodeRegistrationController\nNode self-registration"]
    end

    subgraph HUB["Agent Hub Core"]
        ROUTER["CapabilityTaskRouter\nRoutes by capability type"]
        REGISTRY["NodeRegistry\n(InMemoryNodeRegistry)\nLocalNodeRegistrar seeds locals"]
        CTX["ContextAssembler\n(DefaultContextAssembler)\nFetches Req · TestCase · PR diff"]
    end

    subgraph NODES["Agent Nodes (implement AgentNode)"]
        TCG["TestCaseGenerationNode\nReq → test cases\nLinks req IDs, dedup context"]
        ACG["AutomationCodeGenerationNode\nTestCase + linked reqs → PR code\nOpens GitHub PR"]
        TGN["TestGenNode\nLightweight gen path"]
        ANA["AnalysisNode\nFailure root-cause triage"]
        HEAL["HealingNode\nFlaky fix · PR generation"]
        INS["InsightNode\nCross-project quality narrative"]
        EAC["ExtractAcceptanceCriteriaNode\nParses AC from requirement"]
    end

    subgraph REVIEW["Review Gateway"]
        RGW["KafkaReviewGateway\nemits agent.approval.requests\nconsumes agent.approval.decisions"]
    end

    subgraph IA["Impact Analysis (in platform-ingestion)"]
        IAS["ImpactAnalysisService\nPR diffs + Reqs + existing TCs\n→ Claude API\n→ UPDATE / CREATE suggestions"]
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

## 3. Test Execution Event Flow

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

## 4. AI-Assisted Test Case Lifecycle

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

## 5. Test Case State Machine

```mermaid
stateDiagram-v2
    [*] --> DRAFT : created (human or AI)

    DRAFT --> UNDER_REVIEW : submit for review
    DRAFT --> APPROVED : direct approve
    DRAFT --> REJECTED : reject

    UNDER_REVIEW --> APPROVED : approve
    UNDER_REVIEW --> REJECTED : reject

    APPROVED --> UNDER_REVIEW : impact analysis applies suggestion\n(updatedBy = IMPACT_ANALYSIS)

    APPROVED --> AutomationInProgress : trigger automation generation
    state AutomationInProgress {
        [*] --> IN_PROGRESS : automationWorkflowId set\nautomationPrUrl set
        IN_PROGRESS --> DONE : PR merged
        IN_PROGRESS --> FAILED : workflow error
    }

    REJECTED --> DRAFT : re-open / edit
```

---

## 6. Test Case Linkage Model

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

## 7. Portal — Page Map

```mermaid
flowchart LR
    subgraph NAV["Navigation"]
        direction TB
        OO["OrgOverview"]
        PD["ProjectDetail"]
        REQ["RequirementsPage\n(tree view default + search)"]
        IA["ImpactAnalysesPage"]
        PRA["PRAnalysesPage"]
        TC["TestCasesPage\n(CRUD + requirement linking)"]
        TR["TestRunsPage"]
        TRE["TestRunExecutionPage"]
        RQ["ReviewQueuePage\n(approve/reject AI work)"]
        FT["FlakyTestsPage"]
        AL["AlertsPage"]
        AK["ApiKeysPage"]
        AI["AiSettingsPage"]
        PS["ProjectSettingsPage"]
    end

    subgraph BFF["BFF Endpoints  /api/portal/…"]
        direction TB
        E1["/overview · /teams · /projects"]
        E2["/projects/{id} · /flakiness · /quality-gate\n/pass-rate-trend · /executions · /impact/summary"]
        E3["/requirements · /requirements/stats · /requirements/{id}"]
        E4["/impact-analyses · /impact-analyses/{id}\n/impact-analyses/prs"]
        E5["/pr-analyses"]
        E6["/test-cases · /test-cases/{id}\n/{tcId}/link-requirement · /apply-suggestion\n/{tcId}/submit-review · /approve · /reject\n/{tcId}/generate-automation · /test-suites"]
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

## 8. Observability Stack

```mermaid
flowchart LR
    subgraph SERVICES["All Platform Services :8081–8086"]
        ACT["/actuator/health\n/actuator/metrics\n/actuator/prometheus"]
    end

    subgraph METRICS["Metrics Pipeline"]
        PROM["Prometheus v3.10.0\n:9090\nscrapes all services"]
        GRAFANA["Grafana 12.4.0\n:3000\nDashboards"]
        INFLUX["InfluxDB 1.8\n:8086\nk6 / perf metrics"]
        AM["AlertManager\nPrometheus rules → alerts"]
    end

    subgraph LOGS["Log Pipeline"]
        STDOUT["stdout (JSON structured)"]
        PT["Promtail\ncollects container logs"]
        LOKI["Loki 3.5.0\n:3100\nlog aggregation"]
        LS["Logstash\n:5044\nOpenSearch indexing"]
        OS["OpenSearch 3.5.0\n:9200"]
    end

    subgraph TRACES["Trace Pipeline"]
        OTEL["OpenTelemetry SDK\nAll services instrumented"]
        JAEGER["Jaeger\n:16686\nOTLP gRPC :4317"]
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
        D1["Platform Overview\npass-rate · run volume · flakiness"]
        D2["Flakiness\nCRITICAL_FLAKY heatmap · score dist"]
        D3["AI Analysis\nclassification rate · token usage"]
        D4["Performance Signals\np50/p90/p95/p99 · error rate · VUs"]
        D5["Agent Workflows\npending · running · approval queue"]
    end

    GRAFANA --> DASHBOARDS
```

---

## 9. Deployment Topology

```mermaid
flowchart TD
    subgraph LOCAL["Local Dev (Docker Compose)"]
        direction LR
        subgraph APP_LOCAL["Application Containers"]
            ING_L["ingestion :8081"]
            ANA_L["analytics :8082"]
            INT_L["integration :8083"]
            AI_L["ai :8084"]
            POR_L["portal :8085"]
            AGT_L["agent :8086/8087"]
        end
        subgraph INFRA_LOCAL["Infrastructure Containers"]
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

    subgraph K8S["Production (Kubernetes + Helm)"]
        direction LR
        subgraph WORKLOADS["Deployments + HPA"]
            ING_K["ingestion"]
            ANA_K["analytics"]
            INT_K["integration"]
            AI_K["ai"]
            POR_K["portal"]
            AGT_K["agent"]
        end
        subgraph STATEFUL["StatefulSets"]
            PG_K["PostgreSQL\n(or managed RDS)"]
            KF_K["Kafka KRaft\n3 controllers + 3 brokers"]
            RD_K["Redis\n(or ElastiCache)"]
            OS_K["OpenSearch\n(or managed)"]
            MN_K["MinIO\n(or S3)"]
        end
        subgraph OBS_K["Observability"]
            KPS["kube-prometheus-stack"]
            LOKI_K["Loki"]
            JAEGER_K["Jaeger operator"]
        end
    end
```

---

*Last updated: v2.1 — test case linkage tracking (V45 migration), ReviewQueue page, impact analysis apply-suggestion flow, requirements tree-view default with search.*
