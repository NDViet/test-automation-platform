# Skill: implement-observability

Add Micrometer metrics, structured logging, OpenTelemetry tracing, and Grafana dashboards to any platform module.

## Context

- Metrics: Micrometer → Prometheus 3.10.0 (pull at `/actuator/prometheus`)
- Dashboards: Grafana 12.4.0 — dashboards as JSON in `infrastructure/grafana/dashboards/`
- Logging: Logback with JSON encoder → stdout → Logstash → OpenSearch
- Tracing: OpenTelemetry Java Agent + Jaeger
- Spring Boot Actuator: health, readiness, liveness probes

## Metric Naming Convention

```
platform.<module>.<noun>.<verb>   (total/count/duration)

Examples:
  platform.ingestion.requests.total         (counter)
  platform.ingestion.processing.duration    (timer)
  platform.quality.pass_rate               (gauge)
  platform.flakiness.tests.count           (gauge)
  platform.jira.tickets.created.total      (counter)
```

Always include tags: `team`, `project`, `status` where applicable.

## Instructions

### 1. Add Micrometer dependencies
```xml
<!-- Already in Spring Boot starter — verify these are present -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### 2. Configure Actuator in `application.yml`
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true          # /actuator/health/liveness + /actuator/health/readiness
  metrics:
    tags:
      application: ${spring.application.name}
      environment: ${ENVIRONMENT:local}
    distribution:
      percentiles-histogram:
        platform.ingestion.processing.duration: true
      percentiles:
        platform.ingestion.processing.duration: 0.5,0.95,0.99
```

### 3. Instrument service methods with Micrometer
```java
@Service
public class ResultIngestionService {

    private final MeterRegistry registry;
    private final Counter ingestTotal;
    private final Timer processingTimer;

    public ResultIngestionService(MeterRegistry registry) {
        this.registry = registry;
        this.ingestTotal = Counter.builder("platform.ingestion.requests.total")
            .description("Total ingestion requests")
            .register(registry);
        this.processingTimer = Timer.builder("platform.ingestion.processing.duration")
            .description("Time to parse and persist results")
            .register(registry);
    }

    public String ingest(IngestResultRequest request, Flux<FilePart> files) {
        return processingTimer.record(() -> {
            try {
                String runId = doIngest(request, files);
                ingestTotal.increment(Tags.of("status", "success", "format", request.format().name()));
                return runId;
            } catch (ParseException e) {
                ingestTotal.increment(Tags.of("status", "parse_error", "format", request.format().name()));
                throw e;
            }
        });
    }
}
```

Quality metric gauges (updated after each run):
```java
// Register gauges that read from the DB
Gauge.builder("platform.quality.pass_rate", () ->
    executionRepository.computeOrgPassRate())
    .tag("scope", "org")
    .description("Organization-wide test pass rate")
    .register(registry);

Gauge.builder("platform.flakiness.tests.count", () ->
    flakinessRepository.countByClassification(FLAKY))
    .tag("classification", "flaky")
    .register(registry);
```

### 4. Add structured JSON logging
```xml
<!-- logback-spring.xml -->
<configuration>
  <springProfile name="!local">
    <!-- JSON for all non-local environments (picked up by Logstash) -->
    <appender name="JSON_STDOUT" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdcKeyName>traceId</includeMdcKeyName>
        <includeMdcKeyName>spanId</includeMdcKeyName>
        <includeMdcKeyName>teamId</includeMdcKeyName>
        <includeMdcKeyName>runId</includeMdcKeyName>
      </encoder>
    </appender>
    <root level="INFO">
      <appender-ref ref="JSON_STDOUT"/>
    </root>
  </springProfile>

  <springProfile name="local">
    <!-- Human-readable for local dev -->
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
      <encoder>
        <pattern>%d{HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
      </encoder>
    </appender>
    <root level="DEBUG">
      <appender-ref ref="STDOUT"/>
    </root>
  </springProfile>
</configuration>
```

Add MDC context in request filters:
```java
@Component
public class RequestContextFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String teamId = exchange.getRequest().getHeaders().getFirst("X-Team-Id");
        return chain.filter(exchange)
            .contextWrite(Context.of(
                "teamId", teamId != null ? teamId : "unknown"
            ));
    }
}
```

### 5. Create Grafana dashboard JSON
Create `infrastructure/grafana/dashboards/<name>.json`:

```json
{
  "title": "Platform — Ingestion Health",
  "uid": "platform-ingestion",
  "panels": [
    {
      "title": "Ingestion Rate (req/min)",
      "type": "timeseries",
      "targets": [{
        "expr": "rate(platform_ingestion_requests_total[5m]) * 60",
        "legendFormat": "{{status}} / {{format}}"
      }]
    },
    {
      "title": "Processing Latency p95 (ms)",
      "type": "timeseries",
      "targets": [{
        "expr": "histogram_quantile(0.95, rate(platform_ingestion_processing_duration_seconds_bucket[5m])) * 1000"
      }]
    },
    {
      "title": "Org Pass Rate",
      "type": "stat",
      "targets": [{
        "expr": "platform_quality_pass_rate{scope='org'}",
        "legendFormat": "Pass Rate"
      }],
      "fieldConfig": {
        "defaults": {
          "unit": "percentunit",
          "thresholds": {
            "steps": [
              { "value": 0,   "color": "red" },
              { "value": 0.8, "color": "yellow" },
              { "value": 0.9, "color": "green" }
            ]
          }
        }
      }
    }
  ]
}
```

### 6. Add Prometheus alert rules
Create `infrastructure/prometheus/rules/<module>-alerts.yaml`:

```yaml
groups:
  - name: platform-quality-alerts
    rules:
      - alert: TeamPassRateDropped
        expr: platform_quality_pass_rate < 0.80
        for: 30m
        labels:
          severity: warning
        annotations:
          summary: "{{ $labels.team }} pass rate {{ $value | humanizePercentage }} below 80%"
          runbook: "https://platform.yourorg.com/runbooks/low-pass-rate"

      - alert: CriticalFlakyTestDetected
        expr: platform_flakiness_tests_count{classification="CRITICAL_FLAKY"} > 0
        labels:
          severity: warning
        annotations:
          summary: "{{ $value }} critically flaky test(s) detected in {{ $labels.team }}"

      - alert: IngestionErrorRateHigh
        expr: |
          rate(platform_ingestion_requests_total{status="parse_error"}[5m]) /
          rate(platform_ingestion_requests_total[5m]) > 0.05
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Ingestion error rate {{ $value | humanizePercentage }} > 5%"
```

### 7. Write metrics tests
```java
@SpringBootTest
class IngestionMetricsTest {

    @Autowired MeterRegistry registry;
    @Autowired ResultIngestionService service;

    @Test
    void shouldIncrementSuccessCounterAfterIngest() {
        var before = registry.counter("platform.ingestion.requests.total",
            "status", "success", "format", "JUNIT_XML").count();

        service.ingest(buildValidRequest(JUNIT_XML), buildFiles());

        var after = registry.counter("platform.ingestion.requests.total",
            "status", "success", "format", "JUNIT_XML").count();

        assertThat(after - before).isEqualTo(1.0);
    }
}
```

## Validation
- `/actuator/prometheus` returns metrics in Prometheus text format
- `/actuator/health/liveness` and `/actuator/health/readiness` return 200
- All service operations have a corresponding timer or counter
- JSON logs include `traceId`, `spanId`, `teamId` MDC fields in non-local profiles
- Grafana dashboard JSON is valid and renders without errors
- Alert expressions tested against sample metrics before deployment
