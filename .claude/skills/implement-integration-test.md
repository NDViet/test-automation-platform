# Skill: implement-integration-test

Write integration and end-to-end tests for any platform module using Testcontainers, WireMock, and EmbeddedKafka.

## Context

- Test framework: JUnit 5 + AssertJ
- Testcontainers: for PostgreSQL, Redis, OpenSearch
- WireMock: for external HTTP APIs (JIRA, Claude API, Slack)
- EmbeddedKafka: for Kafka producer/consumer tests
- Spring Boot test slices used to minimize context startup time

## Test Type Decision Guide

| What you're testing | Use |
|---|---|
| JPA repository + DB queries | `@DataJpaTest` + Testcontainers PostgreSQL |
| REST controller only | `@WebFluxTest` + `@MockitoBean` |
| Kafka producer | `@SpringBootTest` + `@EmbeddedKafka` |
| Kafka consumer | `@SpringBootTest` + `@EmbeddedKafka` |
| External HTTP client (JIRA, Claude) | `@SpringBootTest` + `@WireMockTest` |
| Full service integration | `@SpringBootTest` + all containers |
| Algorithm / pure logic | Plain JUnit 5, no Spring context |

## Instructions

### 1. Read the class under test first
Always read the implementation before writing its test. Understand what it does, what it calls, and what can fail.

### 2. PostgreSQL integration tests (`@DataJpaTest`)
```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class TestExecutionRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("platform_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired TestExecutionRepository repository;
    @Autowired TeamRepository teamRepository;

    @Test
    void shouldPersistExecutionAndRetrieveByProject() {
        var team = teamRepository.save(buildTeam("team-a"));
        var project = projectRepository.save(buildProject(team));
        var execution = repository.save(buildExecution(project, "main"));

        var found = repository.findByProjectIdOrderByExecutedAtDesc(project.getId(), PageRequest.of(0, 10));

        assertThat(found.getContent()).hasSize(1);
        assertThat(found.getContent().get(0).getRunId()).isEqualTo(execution.getRunId());
    }

    @Test
    void shouldComputePassRateCorrectly() {
        // Insert 10 test case results, 8 passed, 2 failed
        // Assert pass rate = 0.8
    }
}
```

### 3. Redis integration tests
```java
@SpringBootTest
@Testcontainers
class DuplicateDetectorRedisTest {

    @Container
    static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:8.6.1").withExposedPorts(6379);

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired DuplicateDetector detector;

    @Test
    void shouldMarkRunIdAsProcessed() {
        detector.markProcessed("run-001");
        assertThat(detector.isAlreadyProcessed("run-001")).isTrue();
        assertThat(detector.isAlreadyProcessed("run-999")).isFalse();
    }
}
```

### 4. Kafka producer + consumer tests
```java
@SpringBootTest
@EmbeddedKafka(
    partitions = 3,
    topics = {Topics.TEST_RESULTS_RAW, Topics.FLAKINESS_EVENTS},
    brokerProperties = {"log.dir=target/embedded-kafka"}
)
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.consumer.auto-offset-reset=earliest"
})
class FlakinessConsumerIntegrationTest {

    @Autowired KafkaTemplate<String, TestResultEvent> producer;
    @Autowired FlakinessRepository flakinessRepository;

    @Test
    @Timeout(30)
    void shouldUpdateFlakinessScoreAfterFailedRun() throws InterruptedException {
        var event = buildEventWithFailures("run-001", "test.PaymentTest#processRefund", 3);
        producer.send(Topics.TEST_RESULTS_RAW, event.runId(), event);

        // Wait for consumer to process
        await().atMost(15, SECONDS).untilAsserted(() -> {
            var score = flakinessRepository
                .findByTestIdAndProjectId("test.PaymentTest#processRefund", projectId);
            assertThat(score).isPresent();
            assertThat(score.get().getFailureCount()).isGreaterThan(0);
        });
    }
}
```

### 5. External HTTP API tests (WireMock)
```java
@SpringBootTest
@WireMockTest(httpPort = 9090)
@TestPropertySource(properties = {
    "platform.jira.base-url=http://localhost:9090",
    "platform.claude.base-url=http://localhost:9090"
})
class JiraIntegrationServiceTest {

    @Autowired JiraIntegrationService service;

    @BeforeEach
    void setupStubs() {
        // JIRA create issue
        stubFor(post(urlPathEqualTo("/rest/api/3/issue"))
            .willReturn(okJson("""{"key":"PAY-999","id":"10099"}""")));

        // JIRA search (no existing ticket)
        stubFor(get(urlPathMatching("/rest/api/3/search.*"))
            .willReturn(okJson("""{"issues":[],"total":0}""")));
    }

    @Test
    void shouldCreateNewTicketForFirstTimeFailure() {
        service.processFailure(buildFailureAnalysis(APPLICATION_BUG), buildConfig("PAY"));

        verify(postRequestedFor(urlPathEqualTo("/rest/api/3/issue"))
            .withRequestBody(matchingJsonPath("$.fields.summary",
                containing("PaymentFlowTest"))));

        var links = linkRepository.findAll();
        assertThat(links).hasSize(1);
        assertThat(links.get(0).getIssueKey()).isEqualTo("PAY-999");
    }

    @Test
    void shouldAddCommentInsteadOfCreatingDuplicateTicket() {
        // Pre-existing ticket in DB
        linkRepository.save(buildLink("PAY-999", "test.PaymentTest#processRefund"));

        // JIRA returns existing open ticket
        stubFor(get(urlPathMatching("/rest/api/3/search.*"))
            .willReturn(okJson("""{"issues":[{"key":"PAY-999","status":"In Progress"}]}""")));

        service.processFailure(buildFailureAnalysis(APPLICATION_BUG), buildConfig("PAY"));

        verify(0, postRequestedFor(urlPathEqualTo("/rest/api/3/issue")));  // no create
        verify(postRequestedFor(urlPathMatching("/rest/api/3/issue/PAY-999/comment")));  // comment added
    }
}
```

### 6. Full pipeline end-to-end test
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@EmbeddedKafka(partitions = 3)
class IngestionPipelineE2ETest {

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired WebTestClient webClient;
    @Autowired TestExecutionRepository executionRepository;

    @Test
    @Timeout(30)
    void shouldIngestCucumberReportAndPersistResults() {
        var file = new ClassPathResource("samples/cucumber_json/with_failures.json");

        webClient.post().uri("/api/v1/results/ingest")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(
                MultipartBodyBuilder.of()
                    .part("teamId", "team-payments")
                    .part("projectId", "payment-service")
                    .part("format", "CUCUMBER_JSON")
                    .part("branch", "main")
                    .part("files", file).build()))
            .exchange()
            .expectStatus().isAccepted()
            .expectBody().jsonPath("$.runId").isNotEmpty();

        await().atMost(15, SECONDS).untilAsserted(() -> {
            var executions = executionRepository.findAll();
            assertThat(executions).hasSize(1);
            assertThat(executions.get(0).getFailed()).isGreaterThan(0);
        });
    }
}
```

### 7. Test data builders
Always create a `TestDataBuilders` utility class per module:

```java
// TestDataBuilders.java
public class TestDataBuilders {

    public static Team buildTeam(String slug) {
        return Team.builder().name("Team " + slug).slug(slug).build();
    }

    public static TestExecution buildExecution(Project project, String branch) {
        return TestExecution.builder()
            .runId("run-" + UUID.randomUUID())
            .project(project)
            .branch(branch)
            .environment("staging")
            .sourceFormat(SourceFormat.JUNIT_XML)
            .total(10).passed(8).failed(2).skipped(0)
            .executedAt(Instant.now())
            .build();
    }
}
```

## Coverage Requirements

Every new class must have:
- Unit tests for all public methods with non-trivial logic
- Integration test for repository methods with actual DB queries
- At least one failure/error path tested per service method
- No mocking of the class under test itself

## Validation
- All Testcontainers tests use `static` containers with `@Container` (shared across test methods)
- `@DynamicPropertySource` used for all container property injection — no hardcoded ports
- WireMock verifies both that correct requests were made AND correct response was handled
- Kafka consumer tests wait with `await().atMost(N, SECONDS)` — never `Thread.sleep()`
- No test depends on test execution order — each test is fully independent
