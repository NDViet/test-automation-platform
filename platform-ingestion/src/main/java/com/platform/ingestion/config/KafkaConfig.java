package com.platform.ingestion.config;

import com.platform.common.dto.UnifiedTestResult;
import com.platform.common.kafka.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, UnifiedTestResult> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, UnifiedTestResult> kafkaTemplate(
            ProducerFactory<String, UnifiedTestResult> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }


    @Bean
    public NewTopic testResultsRawTopic() {
        return TopicBuilder.name(Topics.TEST_RESULTS_RAW)
                .partitions(6)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG,
                        String.valueOf(Duration.ofDays(7).toMillis()))
                .build();
    }

    @Bean
    public NewTopic testResultsAnalyzedTopic() {
        return TopicBuilder.name(Topics.TEST_RESULTS_ANALYZED)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic flakinessEventsTopic() {
        return TopicBuilder.name(Topics.FLAKINESS_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic integrationCommandsTopic() {
        return TopicBuilder.name(Topics.INTEGRATION_COMMANDS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic alertEventsTopic() {
        return TopicBuilder.name(Topics.ALERT_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
