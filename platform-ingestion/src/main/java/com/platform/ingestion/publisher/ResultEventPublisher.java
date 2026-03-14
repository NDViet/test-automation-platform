package com.platform.ingestion.publisher;

import com.platform.common.dto.UnifiedTestResult;
import com.platform.common.kafka.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ResultEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ResultEventPublisher.class);

    private final KafkaTemplate<String, UnifiedTestResult> kafkaTemplate;

    public ResultEventPublisher(KafkaTemplate<String, UnifiedTestResult> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(UnifiedTestResult result) {
        kafkaTemplate.send(Topics.TEST_RESULTS_RAW, result.runId(), result)
                .whenComplete((r, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event runId={}", result.runId(), ex);
                    } else {
                        log.debug("Published runId={} to partition={}",
                                result.runId(),
                                r.getRecordMetadata().partition());
                    }
                });
    }
}
