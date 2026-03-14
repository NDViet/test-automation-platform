# Skill: implement-kafka-consumer

Implement a Kafka event consumer in any platform module using Spring Kafka 4.0.x.

## Context

- Kafka version: 4.2.0 (KRaft-only)
- Spring Kafka: 4.0.x
- Deserialization: JSON via `JsonDeserializer` with explicit type mapping
- Topic constants: `com.platform.common.kafka.Topics`
- Each module has its own consumer group — never share consumer groups across modules

## Consumer Group Registry

| Module | Consumer Group |
|---|---|
| platform-core (persistence) | `platform-core-persistence` |
| platform-analytics (flakiness) | `platform-analytics-flakiness` |
| platform-analytics (trends) | `platform-analytics-trends` |
| platform-ai (classification) | `platform-ai-classifier` |
| platform-integration (JIRA) | `platform-integration-jira` |
| platform-integration (alerts) | `platform-integration-alerts` |

## Instructions

### 1. Read existing consumers first
Read any existing consumer in the codebase to understand the established pattern before writing a new one.

### 2. Configure Kafka consumer in `application.yml`
```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: platform-<module>-<purpose>
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.platform.common.kafka.event"
        spring.json.value.default.type: com.platform.common.kafka.event.TestResultEvent
    listener:
      ack-mode: MANUAL_IMMEDIATE     # explicit ack — never auto-commit
      concurrency: 3                 # threads = number of partitions / services
```

### 3. Implement the consumer
```java
@Component
public class TestResultPersistenceConsumer {

    private static final Logger log = LoggerFactory.getLogger(TestResultPersistenceConsumer.class);

    private final ExecutionPersistenceService persistenceService;

    public TestResultPersistenceConsumer(ExecutionPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @KafkaListener(
        topics = Topics.TEST_RESULTS_RAW,
        groupId = "platform-core-persistence",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            @Payload TestResultEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("Consuming event runId={} from topic={} partition={} offset={}",
            event.runId(), topic, partition, offset);

        try {
            persistenceService.persist(event);
            ack.acknowledge();         // ACK only after successful processing
        } catch (DuplicateRunException e) {
            log.warn("Duplicate run detected runId={} — skipping", event.runId());
            ack.acknowledge();         // ACK duplicates — do not retry
        } catch (Exception e) {
            log.error("Failed to process event runId={}", event.runId(), e);
            // Do NOT ack — let Kafka retry up to max.poll.interval.ms
            // Dead-letter topic handles events that exhaust retries
            throw e;
        }
    }
}
```

### 4. Configure dead-letter topic (DLT) handling
```java
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // Dead-letter topic: failed events → test.results.raw.DLT
        factory.setCommonErrorHandler(new DefaultErrorHandler(
            new DeadLetterPublishingRecoverer(kafkaTemplate),
            new FixedBackOff(1000L, 3L)   // retry 3 times with 1s delay
        ));

        return factory;
    }
}
```

### 5. Idempotency guard
Every consumer MUST be idempotent — the same event may be delivered more than once.

```java
// Check for duplicate before processing
if (executionRepository.existsByRunId(event.runId())) {
    log.warn("Duplicate event for runId={} — skipping", event.runId());
    ack.acknowledge();
    return;
}
```

Use a Redis SET or DB unique constraint as the idempotency store.

### 6. Write tests
```java
@SpringBootTest
@EmbeddedKafka(partitions = 3, topics = {Topics.TEST_RESULTS_RAW})
class TestResultPersistenceConsumerTest {

    @Autowired KafkaTemplate<String, TestResultEvent> template;

    @MockitoBean ExecutionPersistenceService persistenceService;

    @Test
    void shouldProcessEventAndAcknowledge() throws Exception {
        var event = buildTestEvent("run-001");
        template.send(Topics.TEST_RESULTS_RAW, event.runId(), event);

        // Wait for consumer to process
        await().atMost(10, SECONDS).untilAsserted(() ->
            verify(persistenceService).persist(argThat(e -> e.runId().equals("run-001")))
        );
    }

    @Test
    void shouldSkipDuplicateEvent() throws Exception {
        doThrow(new DuplicateRunException("run-dup"))
            .when(persistenceService).persist(any());

        template.send(Topics.TEST_RESULTS_RAW, "run-dup", buildTestEvent("run-dup"));

        await().atMost(10, SECONDS).untilAsserted(() ->
            verify(persistenceService, times(1)).persist(any())   // processed once, not retried
        );
    }
}
```

## Validation
- `ack-mode: MANUAL_IMMEDIATE` is set — never use auto-commit
- Consumer group ID is unique to this module+purpose combination
- Duplicate events are handled gracefully (no exception thrown, ACK sent)
- Dead-letter topic configured for exhausted retries
- Test with `@EmbeddedKafka` passes including duplicate scenario
