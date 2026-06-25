package com.platform.agent.config;

import com.platform.common.kafka.Topics;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;

/**
 * Explicit Kafka configuration for platform-agent.
 *
 * <p>Spring Boot 4.x modularises its auto-configuration; KafkaAutoConfiguration is not reliably
 * activated from this service's module path, so we declare the producer and consumer beans
 * explicitly — matching the pattern used by every other platform module.
 *
 * <p>All agent payloads (workflow events, approval requests/decisions) are serialised as JSON
 * strings, so both key and value use StringSerializer / StringDeserializer.
 */
@Configuration
public class KafkaConfig {

  @Value("${spring.kafka.bootstrap-servers}")
  private String bootstrapServers;

  @Value("${platform.agent.consumer-group:platform-agent}")
  private String consumerGroup;

  // -------------------------------------------------------------------------
  // Producer
  // -------------------------------------------------------------------------

  @Bean
  public ProducerFactory<String, String> agentProducerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    props.put(ProducerConfig.RETRIES_CONFIG, 3);
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    return new DefaultKafkaProducerFactory<>(props);
  }

  @Bean
  public KafkaTemplate<String, String> kafkaTemplate(
      ProducerFactory<String, String> agentProducerFactory) {
    return new KafkaTemplate<>(agentProducerFactory);
  }

  // -------------------------------------------------------------------------
  // Consumer
  // -------------------------------------------------------------------------

  @Bean
  public ConsumerFactory<String, String> agentConsumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    return new DefaultKafkaConsumerFactory<>(
        props, new StringDeserializer(), new StringDeserializer());
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
      ConsumerFactory<String, String> agentConsumerFactory) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(agentConsumerFactory);
    factory.setConcurrency(2);
    return factory;
  }

  // -------------------------------------------------------------------------
  // Topics
  // -------------------------------------------------------------------------

  @Bean
  public NewTopic agentWorkflowEventsTopic() {
    return TopicBuilder.name(Topics.AGENT_WORKFLOW_EVENTS).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic agentApprovalRequestsTopic() {
    return TopicBuilder.name(Topics.AGENT_APPROVAL_REQUESTS).partitions(3).replicas(1).build();
  }

  @Bean
  public NewTopic agentApprovalDecisionsTopic() {
    return TopicBuilder.name(Topics.AGENT_APPROVAL_DECISIONS).partitions(3).replicas(1).build();
  }
}
