package com.platform.agent.progress;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis pub/sub implementation: progress events are published to {@code gen:progress:{workflowId}}.
 * The portal subscribes (it already shares the same Redis) and forwards each message to the browser
 * WebSocket subscribed to that workflow. Redis decouples the two services so neither blocks the
 * other and either can restart independently.
 */
@Component
public class RedisGenerationProgressPublisher implements GenerationProgressPublisher {

  private static final Logger log = LoggerFactory.getLogger(RedisGenerationProgressPublisher.class);

  /** Must match the pattern the portal subscriber listens on. */
  public static final String CHANNEL_PREFIX = "gen:progress:";

  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;

  public RedisGenerationProgressPublisher(StringRedisTemplate redis, ObjectMapper mapper) {
    this.redis = redis;
    this.mapper = mapper;
  }

  @Override
  public void started(UUID workflowId) {
    publish(workflowId, event(workflowId, "started", null, 0, null));
  }

  @Override
  public void token(UUID workflowId, String preview, int chars) {
    publish(workflowId, event(workflowId, "token", preview, chars, null));
  }

  @Override
  public void finished(UUID workflowId, String status) {
    publish(workflowId, event(workflowId, "finished", null, 0, status));
  }

  private Map<String, Object> event(
      UUID workflowId, String type, String preview, int chars, String status) {
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("type", type);
    e.put("workflowId", workflowId.toString());
    if (preview != null) e.put("preview", preview);
    if (chars > 0) e.put("chars", chars);
    if (status != null) e.put("status", status);
    return e;
  }

  private void publish(UUID workflowId, Map<String, Object> payload) {
    if (workflowId == null) return;
    try {
      redis.convertAndSend(CHANNEL_PREFIX + workflowId, mapper.writeValueAsString(payload));
    } catch (Exception e) {
      // Progress relay is best-effort — never let it disrupt the generation itself.
      log.debug("failed to publish generation progress for {}: {}", workflowId, e.getMessage());
    }
  }
}
