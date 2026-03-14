# Skill: implement-kafka-producer

Implement a Kafka event producer in any platform module using Spring Kafka 4.0.x with Apache Kafka 4.2.0 (KRaft).

## Context

- Kafka version: 4.2.0 (KRaft-only — no ZooKeeper)
- Spring Kafka: 4.0.x
- Serialization: JSON via Jackson (`JsonSerializer`)
- All topics defined centrally in `platform-common`: `com.platform.common.kafka.Topics`
- Bootstrap servers configured via `KAFKA_BOOTSTRAP_SERVERS` env var (default: `localhost:9092`)

## Topic Registry (platform-common)

```java
// Topics.java — read this file first before producing to any topic
public final class Topics {
    public static final String TEST_RESULTS_RAW       = "test.results.raw";
    public static final String TEST_RESULTS_ANALYZED  = "test.results.analyzed";
    public static final String FLAKINESS_EVENTS       = "test.flakiness.events";
    public static final String INTEGRATION_COMMANDS   = "test.integration.commands";
    public static final String ALERT_EVENTS           = "test.alert.events";
}
```

## Instructions

### 1. Read existing producers first
Read any existing producer in the codebase to understand the established pattern before writing a new one.

### 2. Configure Kafka producer in `application.yml`
```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all                        # strongest durability guarantee
      retries: 3
      properties:
        spring.json.add.type.headers: false   # do not embed Java type in header
        enable.idempotence: true
```

### 3. Declare Kafka topic beans (only if topic is new)
```java
// KafkaTopicConfig.java in the module that owns the topic
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic testResultsRawTopic() {
        return TopicBuilder.name(Topics.TEST_RESULTS_RAW)
            .partitions(6)
            .replicas(1)              // use 3 for production
            .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(Duration.ofDays(7).toMillis()))
            .build();
    }
}
```
Do NOT declare a topic bean if it already exists in another module — just produce to it.

### 4. Implement the producer service
```java
@Service
public class ResultEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ResultEventPublisher.class);

    private final KafkaTemplate<String, TestResultEvent> kafkaTemplate;

    public ResultEventPublisher(KafkaTemplate<String, TestResultEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(TestResultEvent event) {
        // Use runId as the partition key — ensures all events for a run go to same partition
        kafkaTemplate.send(Topics.TEST_RESULTS_RAW, event.runId(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish event runId={}", event.runId(), ex);
                } else {
                    log.debug("Published event runId={} to partition={}",
                        event.runId(), result.getRecordMetadata().partition());
                }
            });
    }
}
```

Rules:
- Partition key: always use the domain object's natural grouping ID (runId, teamId, etc.)
- Use `whenComplete` — never block with `.get()` on the send future in a web request thread
- Log failures at ERROR, successes at DEBUG
- Event classes must be records or immutable POJOs with no-arg constructor for Jackson

### 5. Define the event record
```java
// In platform-common — shared across producer and consumers
public record TestResultEvent(
    String runId,
    String teamId,
    String projectId,
    String branch,
    String environment,
    Instant executedAt,
    int totalTests,
    int failed,
    List<TestCaseEventData> testCases
) {}
```

Rules for event records:
- Always include `runId` for deduplication
- Include only data the consumer needs — do not embed the full domain object
- All fields must be JSON-serializable (no LocalDate — use Instant or String)

### 6. Write tests
```java
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {Topics.TEST_RESULTS_RAW})
class ResultEventPublisherTest {

    @Autowired ResultEventPublisher publisher;

    @Autowired
    @Qualifier("testConsumer")
    KafkaTemplate<String, String> testConsumer;   // or use KafkaTestUtils

    @Test
    void shouldPublishEventToCorrectTopic() {
        // Publish event
        publisher.publish(buildTestEvent());

        // Consume and verify
        ConsumerRecord<String, TestResultEvent> record =
            KafkaTestUtils.getSingleRecord(consumer, Topics.TEST_RESULTS_RAW, Duration.ofSeconds(5));

        assertThat(record.key()).isEqualTo("run-test-123");
        assertThat(record.value().teamId()).isEqualTo("team-a");
    }
}
```

## Validation
- Producer sends to correct topic with correct partition key
- `acks=all` and `enable.idempotence=true` are configured
- No blocking `.get()` calls in the hot path
- Event record is serializable/deserializable without type headers
- `@EmbeddedKafka` test passes
