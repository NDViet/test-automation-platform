# Agent Skills — Test Automation Platform

Reusable skill definitions for AI coding agents implementing the platform.
Each skill is a self-contained instruction set covering: context, implementation rules, code patterns, and validation checklist.

## How to Use

Invoke a skill by name when implementing a specific component:
> "Use the `implement-result-parser` skill to add a Playwright parser"
> "Use the `implement-jpa-domain` skill to create the FlakinessScore entity"

Each skill instructs the agent to **read existing code first** before writing new code.

---

## Skill Catalogue

### Infrastructure & Scaffolding

| Skill | File | When to Use |
|---|---|---|
| scaffold-maven-module | `scaffold-maven-module.md` | Creating a new Spring Boot module in the multi-module project |
| implement-k8s-manifest | `implement-k8s-manifest.md` | Creating K8s Deployment, Service, HPA, ConfigMap for a service |

### Backend — Data Layer

| Skill | File | When to Use |
|---|---|---|
| implement-jpa-domain | `implement-jpa-domain.md` | Creating a JPA entity + repository + Flyway migration |

### Backend — Messaging

| Skill | File | When to Use |
|---|---|---|
| implement-kafka-producer | `implement-kafka-producer.md` | Publishing events to a Kafka topic |
| implement-kafka-consumer | `implement-kafka-consumer.md` | Consuming events from a Kafka topic with manual ACK + DLT |

### Backend — API

| Skill | File | When to Use |
|---|---|---|
| implement-rest-api | `implement-rest-api.md` | Creating a Spring WebFlux REST controller with validation + OpenAPI docs |

### Backend — Domain Logic

| Skill | File | When to Use |
|---|---|---|
| implement-result-parser | `implement-result-parser.md` | Adding a parser for a new test report format |
| implement-flakiness-scorer | `implement-flakiness-scorer.md` | Implementing or extending the flakiness scoring algorithm |

### AI & Intelligence

| Skill | File | When to Use |
|---|---|---|
| implement-ai-classifier | `implement-ai-classifier.md` | Claude API integration for failure classification |

### Integrations

| Skill | File | When to Use |
|---|---|---|
| implement-jira-integration | `implement-jira-integration.md` | JIRA client, ticket lifecycle, duplicate detection |

### Frontend

| Skill | File | When to Use |
|---|---|---|
| implement-react-dashboard | `implement-react-dashboard.md` | React dashboard components, charts, WebSocket hooks |

### SDK

| Skill | File | When to Use |
|---|---|---|
| implement-sdk-extension | `implement-sdk-extension.md` | JUnit5 / TestNG / Cucumber auto-publish extensions |

### Testing

| Skill | File | When to Use |
|---|---|---|
| implement-integration-test | `implement-integration-test.md` | Testcontainers, WireMock, EmbeddedKafka test patterns |

### Observability

| Skill | File | When to Use |
|---|---|---|
| implement-observability | `implement-observability.md` | Micrometer metrics, structured logging, Grafana dashboards, Prometheus alerts |

---

## Cross-Cutting Rules (apply to all skills)

1. **Read before write** — always read existing code in the target package before creating new files
2. **No surprises** — align naming, patterns, and structure with what already exists in the module
3. **Constructor injection** — never `@Autowired` on fields; always constructor injection
4. **Records for DTOs** — use Java records for all request/response/event DTOs
5. **Never expose entities** — domain entities never returned directly from REST controllers
6. **Non-blocking SDK** — `platform-sdk` never throws; exceptions always caught and logged at WARN
7. **Explicit Kafka ACK** — always `MANUAL_IMMEDIATE`; never auto-commit
8. **Idempotent consumers** — every Kafka consumer must handle duplicate message delivery
9. **Testcontainers static** — always `static` `@Container` fields (shared across test methods)
10. **No magic numbers** — scoring thresholds, timeouts, and limits as named constants

---

## Phase → Skill Mapping

| Phase | Primary Skills |
|---|---|
| Phase 1 — Foundation | scaffold-maven-module, implement-jpa-domain, implement-rest-api, implement-result-parser (JUnit XML) |
| Phase 2 — Multi-framework | implement-result-parser (all formats), implement-sdk-extension, implement-integration-test |
| Phase 3 — Analytics | implement-kafka-consumer, implement-kafka-producer, implement-flakiness-scorer, implement-react-dashboard |
| Phase 4 — JIRA | implement-jira-integration |
| Phase 5 — AI | implement-ai-classifier |
| Phase 6 — Observability | implement-observability, implement-k8s-manifest |
