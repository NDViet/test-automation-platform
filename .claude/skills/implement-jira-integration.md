# Skill: implement-jira-integration

Implement JIRA REST API integration in `platform-integration` for automatic ticket lifecycle management.

## Context

- Module: `platform-integration`
- Package: `com.platform.integration.jira`
- JIRA Cloud API: REST API v3 (`/rest/api/3/`)
- JIRA Server/DC API: REST API v2 (`/rest/api/2/`)
- Auth: OAuth 2.0 (Cloud) or Personal Access Token (Server/DC)
- HTTP client: Spring WebClient (non-blocking)
- Team JIRA config stored in `integration_configs` table

## Instructions

### 1. Read existing integration code and plan first
- Read `platform-integration/src/main/java/com/platform/integration/` fully
- Read Section 9 of `/Users/viet.dnguyen/code/test-automation-platform/PLATFORM_PLAN.md` for ticket template and decision rules
- Read `platform-integration/src/main/java/com/platform/integration/port/IssueTrackerPort.java`

### 2. Implement `JiraClient`
```java
@Component
public class JiraClient {

    private final WebClient webClient;

    public JiraClient(WebClient.Builder webClientBuilder,
                      JiraAuthConfig authConfig) {
        this.webClient = webClientBuilder
            .baseUrl(authConfig.baseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, authConfig.authHeader())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    public Mono<JiraIssueResponse> createIssue(JiraCreateIssueRequest request) {
        return webClient.post()
            .uri("/rest/api/3/issue")
            .bodyValue(request)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, this::handleClientError)
            .bodyToMono(JiraIssueResponse.class)
            .retryWhen(retryOnServerError());
    }

    public Mono<Void> addComment(String issueKey, String commentBody) {
        return webClient.post()
            .uri("/rest/api/3/issue/{key}/comment", issueKey)
            .bodyValue(Map.of("body", buildAdfBody(commentBody)))
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, this::handleClientError)
            .bodyToMono(Void.class);
    }

    public Mono<Void> transitionIssue(String issueKey, String transitionId) {
        return webClient.post()
            .uri("/rest/api/3/issue/{key}/transitions", issueKey)
            .bodyValue(Map.of("transition", Map.of("id", transitionId)))
            .retrieve()
            .bodyToMono(Void.class);
    }

    public Mono<JiraSearchResponse> searchIssues(String jql) {
        return webClient.get()
            .uri(u -> u.path("/rest/api/3/search")
                .queryParam("jql", jql)
                .queryParam("fields", "id,key,status,summary,labels")
                .queryParam("maxResults", 10)
                .build())
            .retrieve()
            .bodyToMono(JiraSearchResponse.class);
    }

    private Retry retryOnServerError() {
        return Retry.backoff(3, Duration.ofSeconds(1))
            .filter(e -> e instanceof WebClientResponseException &&
                         ((WebClientResponseException) e).getStatusCode().is5xxServerError());
    }
}
```

### 3. Implement `DuplicateDetector`
```java
@Service
public class DuplicateDetector {

    private final IssueTrackerLinkRepository linkRepository;
    private final JiraClient jiraClient;

    public Mono<Optional<IssueReference>> findExistingOpenTicket(
            String testId, UUID projectId, String projectKey) {

        // 1. Check platform DB first (fast path)
        return linkRepository.findByTestIdAndProjectId(testId, projectId)
            .flatMap(link -> {
                if (link != null) {
                    // Verify it's still open in JIRA
                    return jiraClient.searchIssues(
                        "key = %s AND status != Done".formatted(link.getIssueKey()))
                        .map(r -> r.issues().isEmpty()
                            ? Optional.<IssueReference>empty()
                            : Optional.of(new IssueReference(link.getIssueKey(), link.getIssueUrl())));
                }
                // 2. Fallback: search JIRA by label + summary keyword
                String jql = """
                    project = %s
                    AND labels = "automated-failure"
                    AND summary ~ "%s"
                    AND status != Done
                    ORDER BY created DESC
                    """.formatted(projectKey, extractKeyword(testId));

                return jiraClient.searchIssues(jql)
                    .map(r -> r.issues().stream().findFirst()
                        .map(i -> new IssueReference(i.key(), i.url())));
            });
    }
}
```

### 4. Implement `IssueDecisionEngine`
```java
@Service
public class IssueDecisionEngine {

    public Mono<IssueAction> decide(
            FailureAnalysis analysis,
            FlakinessScore flakinessScore,
            TestCaseHistory history,
            TeamIntegrationConfig config) {

        return switch (analysis.category()) {

            case APPLICATION_BUG -> {
                if (history.consecutiveFailures() < config.minConsecutiveFailures()) {
                    yield Mono.just(IssueAction.SKIP);   // too early — wait for threshold
                }
                yield duplicateDetector.findExistingOpenTicket(...)
                    .map(existing -> existing.isPresent()
                        ? IssueAction.addComment(existing.get(), buildRunComment(history))
                        : IssueAction.create(buildBugRequest(analysis, history, config)));
            }

            case FLAKY_TIMING, ENVIRONMENT -> {
                if (flakinessScore.score() < config.flakinessThreshold()) {
                    yield Mono.just(IssueAction.SKIP);
                }
                yield duplicateDetector.findExistingOpenTicket(...)
                    .map(existing -> existing.isPresent()
                        ? IssueAction.updateFlakinessScore(existing.get(), flakinessScore)
                        : IssueAction.create(buildTestMaintenanceRequest(analysis, flakinessScore, config)));
            }

            case TEST_DEFECT -> {
                if (analysis.confidence() < 0.70) yield Mono.just(IssueAction.SKIP);
                yield duplicateDetector.findExistingOpenTicket(...)
                    .map(existing -> existing.isPresent()
                        ? IssueAction.addComment(existing.get(), buildRunComment(history))
                        : IssueAction.create(buildTestFixRequest(analysis, history, config)));
            }

            case DEPENDENCY ->
                // Single incident ticket per root cause — handled separately
                Mono.just(IssueAction.SKIP);

            default -> Mono.just(IssueAction.SKIP);
        };
    }
}
```

### 5. Implement `TicketLifecycleManager`
```java
@Service
public class TicketLifecycleManager {

    // Called per test run completion
    public Mono<Void> processTestResult(TestCaseResult result, FailureAnalysis analysis,
                                         FlakinessScore score, TeamIntegrationConfig config) {

        if (result.getStatus() == PASSED) {
            return handleTestPassed(result, config);
        } else {
            return decisionEngine.decide(analysis, score, history, config)
                .flatMap(this::executeAction);
        }
    }

    private Mono<Void> handleTestPassed(TestCaseResult result, TeamIntegrationConfig config) {
        if (!config.autoCloseOnGreen()) return Mono.empty();

        return linkRepository.findByTestIdAndProjectId(result.getTestId(), result.getExecution().getProjectId())
            .flatMap(link -> {
                if (link == null) return Mono.empty();

                // Auto-close only after N consecutive passes (default 3)
                int consecutivePasses = historyRepository.countConsecutivePasses(
                    result.getTestId(), result.getExecution().getProjectId());

                if (consecutivePasses >= config.consecutivePassesForClose()) {
                    return jiraClient.transitionIssue(link.getIssueKey(), config.doneTransitionId())
                        .then(jiraClient.addComment(link.getIssueKey(),
                            "Test resolved. Passed %d consecutive times. Last run: %s"
                                .formatted(consecutivePasses, result.getExecution().getCiRunUrl())));
                }
                return Mono.empty();
            });
    }
}
```

### 6. Build the JIRA ticket description
```java
private String buildTicketDescription(FailureAnalysis analysis, TestCaseResult failure) {
    return """
        *Automated Test Failure Report*

        *Test:* {{%s}}
        *Project:* %s | *Team:* %s
        *Environment:* %s | *Branch:* %s
        *Consecutive failures:* %d
        *First seen:* %s

        h3. Failure Message
        {noformat}%s{noformat}

        h3. Stack Trace
        {noformat}%s{noformat}

        h3. AI Root Cause Analysis
        *Category:* %s (confidence: %.0f%%)
        *Root Cause:* %s
        *Analysis:* %s
        *Suggested Fix:* %s

        h3. Links
        * [Platform test page|%s/tests/%s]
        * [Latest CI run|%s]
        """.formatted(/* all fields */);
}
```

### 7. Write integration tests (WireMock)
```java
@SpringBootTest
@WireMockTest(httpPort = 8089)
class JiraClientTest {

    @Autowired JiraClient jiraClient;

    @Test
    void shouldCreateIssueAndReturnKey() {
        stubFor(post(urlEqualTo("/rest/api/3/issue"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""{"id":"10001","key":"PAY-1234","self":"..."}""")));

        var response = jiraClient.createIssue(buildRequest()).block();

        assertThat(response.key()).isEqualTo("PAY-1234");
        verify(postRequestedFor(urlEqualTo("/rest/api/3/issue")));
    }

    @Test
    void shouldRetryOnServerError() {
        stubFor(post(urlEqualTo("/rest/api/3/issue"))
            .inScenario("retry").whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withStatus(500))
            .willSetStateTo("second"));
        stubFor(post(urlEqualTo("/rest/api/3/issue"))
            .inScenario("retry").whenScenarioStateIs("second")
            .willReturn(aResponse().withStatus(201).withBody("""{"key":"PAY-1235"}""")));

        var response = jiraClient.createIssue(buildRequest()).block();
        assertThat(response.key()).isEqualTo("PAY-1235");
    }
}
```

## Validation
- Duplicate detector checks platform DB before hitting JIRA API
- Retry on 5xx with exponential backoff — max 3 retries
- 4xx errors are not retried (surfaced as errors to caller)
- Auto-close requires `autoCloseOnGreen=true` in team config (default: false)
- WireMock tests pass without requiring a real JIRA instance
- JIRA credentials never logged — use `authConfig.authHeader()` which redacts in toString
