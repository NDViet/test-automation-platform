package com.platform.portal.ws;

import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Subscribes to the agent's generation-progress channels ({@code gen:progress:*}) and forwards each
 * message to the browser WebSocket(s) watching that workflow. The workflow id is the trailing part
 * of the channel name.
 */
@Configuration
public class GenerationProgressSubscriber {

  private final GenerationWsHandler handler;

  public GenerationProgressSubscriber(GenerationWsHandler handler) {
    this.handler = handler;
  }

  @Bean
  public RedisMessageListenerContainer generationProgressListenerContainer(
      RedisConnectionFactory connectionFactory) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(
        (message, pattern) -> {
          String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
          String body = new String(message.getBody(), StandardCharsets.UTF_8);
          String workflowId = channel.substring(channel.lastIndexOf(':') + 1);
          handler.broadcast(workflowId, body);
        },
        new PatternTopic("gen:progress:*"));
    return container;
  }
}
