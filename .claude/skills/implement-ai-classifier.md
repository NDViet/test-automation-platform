# Skill: implement-ai-classifier

Implement AI-powered test failure classification in `platform-ai` using the Anthropic Claude API.

## Context

- Module: `platform-ai`
- Package: `com.platform.ai`
- Model: `claude-sonnet-4-6` (use this exact model ID)
- SDK: Anthropic Java SDK (see `claude-api` skill for SDK setup)
- Rate limit: max 10 classification requests/second — enforced via Resilience4j
- Triggered: real-time after each failed test (via Kafka) + nightly Spring Batch job

## Instructions

### 1. Use the `claude-api` skill first
Before writing any Claude API code, invoke the `claude-api` skill to ensure the Anthropic SDK dependency and client configuration are set up correctly.

### 2. Read existing AI code
Read all files in `platform-ai/src/main/java/com/platform/ai/` before writing new code.

### 3. Define the output contract
```java
// FailureAnalysis.java — stored in DB and returned by API
public record FailureAnalysis(
    UUID testCaseResultId,
    FailureCategory category,
    double confidence,          // 0.0–1.0
    String rootCause,           // one sentence
    String detailedAnalysis,    // paragraph
    String suggestedFix,        // actionable steps
    boolean isFlakyCandidate,
    String affectedComponent,   // class or service name, nullable
    Instant analyzedAt
) {}

public enum FailureCategory {
    APPLICATION_BUG,   // code regression
    TEST_DEFECT,       // wrong assertion, bad test data
    ENVIRONMENT,       // infra flap, port conflict, timeout
    FLAKY_TIMING,      // race condition, async timing
    DEPENDENCY,        // external service down
    UNKNOWN
}
```

### 4. Implement `PromptBuilder`
```java
@Component
public class PromptBuilder {

    private static final int MAX_STACK_TRACE_LINES = 40;
    private static final int MAX_HISTORY_RUNS = 10;

    public String buildClassificationPrompt(TestCaseResult failure, List<TestCaseResult> history) {
        return """
            You are a test automation expert. Analyze this test failure and respond ONLY with valid JSON.

            TEST CONTEXT:
            - Test: %s
            - Project: %s | Team: %s
            - Framework: %s
            - Environment: %s
            - Branch: %s
            - Consecutive failures: %d

            FAILURE MESSAGE:
            %s

            STACK TRACE (truncated to %d lines):
            %s

            RUN HISTORY (last %d runs):
            %s

            Respond with this exact JSON structure and no other text:
            {
              "category": "APPLICATION_BUG | TEST_DEFECT | ENVIRONMENT | FLAKY_TIMING | DEPENDENCY | UNKNOWN",
              "confidence": <number 0.0-1.0>,
              "rootCause": "<one sentence>",
              "detailedAnalysis": "<one paragraph>",
              "suggestedFix": "<actionable steps>",
              "isFlakyCandidate": <true|false>,
              "affectedComponent": "<class or service name or null>"
            }
            """.formatted(
                failure.getTestId(),
                failure.getExecution().getProject().getName(),
                failure.getExecution().getProject().getTeam().getName(),
                failure.getExecution().getSourceFormat(),
                failure.getExecution().getEnvironment(),
                failure.getExecution().getBranch(),
                consecutiveCount(history),
                failure.getFailureMessage(),
                MAX_STACK_TRACE_LINES,
                truncateStackTrace(failure.getStackTrace(), MAX_STACK_TRACE_LINES),
                MAX_HISTORY_RUNS,
                formatHistory(history, MAX_HISTORY_RUNS)
            );
    }

    private String truncateStackTrace(String trace, int maxLines) {
        if (trace == null) return "(no stack trace)";
        return Arrays.stream(trace.split("\n"))
            .limit(maxLines)
            .collect(Collectors.joining("\n"));
    }

    private String formatHistory(List<TestCaseResult> history, int max) {
        return history.stream()
            .limit(max)
            .map(r -> "  Run #%s — %s — %s — %dms".formatted(
                r.getExecution().getRunId(),
                r.getStatus(),
                r.getCreatedAt().toString(),
                r.getDurationMs()))
            .collect(Collectors.joining("\n"));
    }
}
```

### 5. Implement `FailureClassificationService`
```java
@Service
public class FailureClassificationService {

    private static final Logger log = LoggerFactory.getLogger(FailureClassificationService.class);

    private final AnthropicClient claudeClient;
    private final PromptBuilder promptBuilder;
    private final FailureAnalysisRepository analysisRepository;
    private final ObjectMapper objectMapper;

    @RateLimiter(name = "claude-api")            // Resilience4j — 10 req/s
    public FailureAnalysis classify(TestCaseResult failure, List<TestCaseResult> history) {
        String prompt = promptBuilder.buildClassificationPrompt(failure, history);

        Message response = claudeClient.messages().create(MessageCreateParams.builder()
            .model("claude-sonnet-4-6")
            .maxTokens(1024)
            .addUserMessage(prompt)
            .build());

        String content = response.content().get(0).text().orElseThrow();

        return parseAndPersist(content, failure.getId());
    }

    private FailureAnalysis parseAndPersist(String jsonContent, UUID testCaseResultId) {
        try {
            var raw = objectMapper.readValue(jsonContent, FailureAnalysisRaw.class);
            var analysis = FailureAnalysis.builder()
                .testCaseResultId(testCaseResultId)
                .category(parseCategory(raw.category()))
                .confidence(clamp(raw.confidence(), 0.0, 1.0))
                .rootCause(raw.rootCause())
                .detailedAnalysis(raw.detailedAnalysis())
                .suggestedFix(raw.suggestedFix())
                .isFlakyCandidate(raw.isFlakyCandidate())
                .affectedComponent(raw.affectedComponent())
                .analyzedAt(Instant.now())
                .build();
            return analysisRepository.save(analysis);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse Claude response — returning UNKNOWN classification", e);
            return buildUnknownAnalysis(testCaseResultId);
        }
    }
}
```

### 6. Configure Resilience4j rate limiter
```yaml
# application.yml
resilience4j:
  ratelimiter:
    instances:
      claude-api:
        limit-for-period: 10
        limit-refresh-period: 1s
        timeout-duration: 5s
```

### 7. Implement the Kafka consumer (real-time)
```java
@Component
public class FailureClassificationConsumer {

    @KafkaListener(topics = Topics.TEST_RESULTS_RAW, groupId = "platform-ai-classifier")
    public void onResults(@Payload TestResultEvent event, Acknowledgment ack) {
        // Only classify failures — skip passed/skipped
        event.testCases().stream()
            .filter(tc -> tc.status() == FAILED || tc.status() == BROKEN)
            .forEach(tc -> {
                var failure = testCaseResultRepository.findById(tc.id()).orElseThrow();
                var history = testCaseResultRepository
                    .findLast10ByTestIdAndProjectId(tc.testId(), event.projectId());
                classificationService.classify(failure, history);
            });
        ack.acknowledge();
    }
}
```

### 8. Implement nightly batch job (Spring Batch)
```java
@Configuration
public class NightlyAnalysisBatchConfig {

    @Bean
    public Job nightlyAnalysisJob(JobRepository jobRepository, Step classifyStep) {
        return new JobBuilder("nightly-analysis-job", jobRepository)
            .start(classifyStep)
            .build();
    }

    @Bean
    public Step classifyFailuresStep(
            JobRepository jobRepository,
            PlatformTransactionManager txManager,
            ItemReader<TestCaseResult> reader,
            ItemProcessor<TestCaseResult, FailureAnalysis> processor,
            ItemWriter<FailureAnalysis> writer) {

        return new StepBuilder("classify-failures", jobRepository)
            .<TestCaseResult, FailureAnalysis>chunk(10, txManager)  // 10 at a time (rate limit)
            .reader(reader)        // unanalyzed failures from last 24h
            .processor(processor)  // calls Claude API
            .writer(writer)        // persists FailureAnalysis
            .faultTolerant()
            .retry(Exception.class)
            .retryLimit(2)
            .build();
    }
}
```

Schedule: `@Scheduled(cron = "0 0 2 * * *")` — 2:00 AM daily.

### 9. Write tests (mock Claude API)
```java
@ExtendWith(MockitoExtension.class)
class FailureClassificationServiceTest {

    @Mock AnthropicClient claudeClient;
    @Mock FailureAnalysisRepository analysisRepository;
    @InjectMocks FailureClassificationService service;

    @Test
    void classifiesApplicationBugCorrectly() {
        // Mock Claude returning APPLICATION_BUG JSON
        when(claudeClient.messages().create(any())).thenReturn(mockResponse("""
            {
              "category": "APPLICATION_BUG",
              "confidence": 0.92,
              "rootCause": "Refund endpoint returns 422 due to validation change",
              "detailedAnalysis": "...",
              "suggestedFix": "Update test amount to be > 0",
              "isFlakyCandidate": false,
              "affectedComponent": "RefundController"
            }
        """));

        var result = service.classify(buildFailure(), List.of());

        assertThat(result.category()).isEqualTo(APPLICATION_BUG);
        assertThat(result.confidence()).isEqualTo(0.92);
        assertThat(result.isFlakyCandidate()).isFalse();
    }

    @Test
    void returnsUnknownWhenClaudeResponseIsInvalidJson() {
        when(claudeClient.messages().create(any())).thenReturn(mockResponse("not json"));

        var result = service.classify(buildFailure(), List.of());

        assertThat(result.category()).isEqualTo(UNKNOWN);
    }
}
```

## Validation
- Claude API calls are rate-limited to ≤ 10/second via Resilience4j
- Invalid/unparseable Claude responses fall back to `UNKNOWN` — never throw to caller
- Stack trace is truncated to 40 lines before sending to API (cost control)
- Batch job processes only unanalyzed failures — not reprocessing existing analyses
- Unit tests use mocked `AnthropicClient` — no real API calls in tests
