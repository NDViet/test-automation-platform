package com.platform.agent.node.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.agent.node.CheckpointService;
import com.platform.common.agent.ContextBundle;
import com.platform.common.agent.ResumeStrategy;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed CheckpointService. PROMPT_CACHE / COMPRESSED: stored in Redis with TTL. HANDOFF:
 * stored in Redis with longer TTL (and optionally persisted to PostgreSQL by a scheduled job).
 */
@Component
public class RedisCheckpointService implements CheckpointService {

  private static final Logger log = LoggerFactory.getLogger(RedisCheckpointService.class);
  private static final String KEY_PREFIX = "agent:checkpoint:";
  private static final Duration PROMPT_CACHE_TTL = Duration.ofMinutes(6);
  private static final Duration COMPRESSED_TTL = Duration.ofHours(25);
  private static final Duration HANDOFF_TTL = Duration.ofDays(30);

  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;

  public RedisCheckpointService(StringRedisTemplate redis, ObjectMapper mapper) {
    this.redis = redis;
    this.mapper = mapper;
  }

  @Override
  public String save(ContextBundle bundle, ConversationState state, ResumeStrategy strategy) {
    String id = UUID.randomUUID().toString();
    Duration ttl =
        switch (strategy) {
          case PROMPT_CACHE -> PROMPT_CACHE_TTL;
          case COMPRESSED -> COMPRESSED_TTL;
          case HANDOFF -> HANDOFF_TTL;
        };
    try {
      redis.opsForValue().set(KEY_PREFIX + id, mapper.writeValueAsString(state), ttl);
      log.debug("checkpoint saved: {} strategy={} ttl={}", id, strategy, ttl);
    } catch (Exception e) {
      log.error("failed to save checkpoint {}", id, e);
    }
    return id;
  }

  @Override
  public Optional<ConversationState> load(String checkpointId) {
    String json = redis.opsForValue().get(KEY_PREFIX + checkpointId);
    if (json == null) return Optional.empty();
    try {
      return Optional.of(mapper.readValue(json, ConversationState.class));
    } catch (Exception e) {
      log.error("failed to deserialize checkpoint {}", checkpointId, e);
      return Optional.empty();
    }
  }

  @Override
  public void extend(String checkpointId, Duration extension) {
    redis.expire(KEY_PREFIX + checkpointId, extension);
  }

  @Override
  public void delete(String checkpointId) {
    redis.delete(KEY_PREFIX + checkpointId);
  }
}
