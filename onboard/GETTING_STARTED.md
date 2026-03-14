# Getting Started — Onboard Example

End-to-end walkthrough: bring up the local platform stack, seed the database, run the
TestNG + Playwright tests against [the-internet.herokuapp.com](https://the-internet.herokuapp.com),
and verify results are stored in PostgreSQL.

---

## Prerequisites

| Tool | Minimum version | Notes |
|------|----------------|-------|
| Java | 21 | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Docker Desktop | 4.x | With Compose v2 |
| Chromium (bundled) | — | Downloaded automatically by Playwright on first run |

> **Docker disk space:** the stack needs ~4 GB of image space. Run `docker system prune -f` if
> Docker Desktop reports low disk.

---

## 1. Build the Platform Modules

From the repository root, build and install all modules into your local Maven repository:

```bash
cd /path/to/test-automation-platform

mvn install -DskipTests
```

This makes `platform-core`, `platform-common`, `platform-sdk`, and `platform-testframework`
available as local dependencies for the `onboard/` project.

---

## 2. Start the Infrastructure Stack

The `docker-compose.yml` at the repo root defines two tiers:

| Tier | How to start | Services |
|------|-------------|---------|
| Infrastructure (always-on) | `docker compose up -d` | PostgreSQL · Kafka · Redis · OpenSearch · Prometheus · Grafana |
| Platform services | `docker compose --profile services up -d` | platform-ingestion (8081) · platform-analytics (8082) · platform-integration (8083) · platform-ai (8084) |

For the onboard example you only need the **infrastructure tier** plus **platform-ingestion**:

```bash
# From the repo root
docker compose up -d
docker compose --profile services up -d platform-ingestion
```

Wait until all containers are healthy:

```bash
docker compose ps
```

Expected output (abbreviated):

```
NAME                   STATUS
platform-ingestion     Up (healthy)   0.0.0.0:8081->8081/tcp
platform-kafka         Up (healthy)   0.0.0.0:9092->9092/tcp
platform-postgres      Up (healthy)   0.0.0.0:5432->5432/tcp
platform-redis         Up (healthy)   0.0.0.0:6379->6379/tcp
platform-opensearch    Up (healthy)   0.0.0.0:9200->9200/tcp
platform-grafana       Up             0.0.0.0:3000->3000/tcp
platform-prometheus    Up             0.0.0.0:9090->9090/tcp
```

Confirm the ingestion service is up:

```bash
curl -s http://localhost:8081/actuator/health | python3 -m json.tool
# → { "status": "UP" }
```

Swagger UI is available at: http://localhost:8081/swagger-ui/index.html

---

## 3. Seed the Database

The ingestion service validates that the `teamId` and `projectId` in each result payload
exist in the database. Seed them once after first startup:

```bash
docker exec platform-postgres psql -U platform -d platform -c "
INSERT INTO teams (name, slug) VALUES ('The Internet', 'team-the-internet');
INSERT INTO projects (team_id, name, slug)
  SELECT id, 'The Internet', 'proj-the-internet'
  FROM   teams WHERE slug = 'team-the-internet';
"
```

Verify:

```bash
docker exec platform-postgres psql -U platform -d platform \
  -c "SELECT t.slug AS team, p.slug AS project FROM teams t JOIN projects p ON p.team_id = t.id;"
```

```
       team        |      project
-------------------+-------------------
 team-the-internet | proj-the-internet
```

> **Note:** You only need to do this once. The data persists in the `postgres_data` Docker
> volume across restarts.

---

## 4. Configure the Onboard Project

The onboard project reads its platform connection from:

```
onboard/src/test/resources/platform.properties
```

Default values that match the local stack:

```properties
platform.url=http://localhost:8081
platform.api-key=dev-local-no-auth   # auth is disabled in local dev
platform.team-id=team-the-internet
platform.project-id=proj-the-internet
platform.environment=local
```

You can override any value with environment variables before running Maven:

```bash
export PLATFORM_URL=http://localhost:8081
export PLATFORM_API_KEY=dev-local-no-auth
export PLATFORM_TEAM_ID=team-the-internet
export PLATFORM_PROJECT_ID=proj-the-internet
```

---

## 5. Run the Tests

```bash
cd onboard

# Run all 9 tests (headless by default)
mvn test

# Run smoke tests only
mvn test -Dgroups=smoke

# Run with a visible browser window
mvn test -Dheadless=false
```

### Test suite

| Class | Tests | Description |
|-------|-------|-------------|
| `LoginTest` | 3 | Valid login → redirect, invalid credentials error messages |
| `CheckboxTest` | 3 | Initial checkbox state, toggle, double-toggle |
| `DynamicLoadingTest` | 3 | Hidden-element example, rendered-element example, both examples match |

### Expected output (tail)

```
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 103.8 s -- in TestSuite
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Each test logs a publish confirmation to stdout:

```
INFO  [validLoginRedirectsToSecurePage] c.p.sdk.publisher.PlatformReporter
      — [Platform SDK] Native result published — runId=c51be5ae-... tests=1
```

---

## 6. Verify Results in PostgreSQL

```bash
docker exec platform-postgres psql -U platform -d platform -c "
SELECT tcr.display_name,
       tcr.status,
       tcr.duration_ms,
       te.source_format,
       te.environment
FROM   test_case_results tcr
JOIN   test_executions   te  ON te.id = tcr.execution_id
ORDER  BY tcr.created_at DESC
LIMIT  9;
"
```

Sample output:

```
                          display_name                           | status | duration_ms |  source_format  | environment
-----------------------------------------------------------------+--------+-------------+-----------------+-------------
 Example 2 — element is rendered into the DOM after loading      | PASSED |       15910 | PLATFORM_NATIVE | local
 Example 1 — hidden element becomes visible after loading        | PASSED |       12717 | PLATFORM_NATIVE | local
 Both examples produce the same finish message                   | PASSED |       17006 | PLATFORM_NATIVE | local
 Clicking a checkbox toggles its checked state                   | PASSED |        4534 | PLATFORM_NATIVE | local
 Page renders two checkboxes with the expected initial state     | PASSED |        5317 | PLATFORM_NATIVE | local
 Toggling each checkbox twice returns it to its original state   | PASSED |       10783 | PLATFORM_NATIVE | local
 Wrong password with valid username shows specific error         | PASSED |        4649 | PLATFORM_NATIVE | local
 Valid credentials authenticate the user and redirect to /secure | PASSED |        5452 | PLATFORM_NATIVE | local
 Invalid credentials display an error and stay on /login         | PASSED |        7770 | PLATFORM_NATIVE | local
```

Quick counts:

```bash
docker exec platform-postgres psql -U platform -d platform \
  -c "SELECT count(*) AS executions FROM test_executions; SELECT count(*) AS results FROM test_case_results;"
```

---

## 7. (Optional) View Metrics in Grafana

Grafana is available at http://localhost:3000 (admin / admin).

Prometheus scrapes `platform-ingestion` at http://localhost:8081/actuator/prometheus and is
available at http://localhost:9090.

---

## Troubleshooting

### `Team not found` (HTTP 400 from ingestion)

The seed data is missing. Run step 3 again.

### `Platform returned HTTP 500`

Flyway migrations have not run. This usually means the ingestion container started before
the database was ready, or the `postgres_data` volume was deleted.

```bash
# Restart the ingestion service to trigger migrations
docker compose --profile services restart platform-ingestion
docker logs platform-ingestion | grep -E "Flyway|Migrat"
```

### Port 8081 already in use

Another container is using the port:

```bash
docker ps --filter "publish=8081"
docker stop <container_name>
docker compose --profile services up -d platform-ingestion
```

### Tests fail to connect to the browser / Playwright error

Playwright downloads Chromium on first run. If the download was interrupted:

```bash
mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.args="install chromium" -f onboard/pom.xml
```

### Disk full inside Docker

```bash
docker system prune -f --volumes   # WARNING: removes all stopped containers and unused volumes
```

---

## Stack Teardown

```bash
# Stop only platform services (keep infra running)
docker compose --profile services down

# Stop everything (data is preserved in volumes)
docker compose down

# Stop everything AND delete all data volumes
docker compose down -v
```
