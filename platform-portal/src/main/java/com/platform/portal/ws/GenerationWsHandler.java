package com.platform.portal.ws;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Tracks browser WebSocket sessions per generation workflow and fan-outs progress messages to them.
 * Connections are made to {@code /ws/generations/{workflowId}}; the workflow id is parsed from the
 * path. Messages originate from {@link GenerationProgressSubscriber} (Redis pub/sub fed by the
 * agent's token stream).
 */
@Component
public class GenerationWsHandler extends TextWebSocketHandler {

  private static final Logger log = LoggerFactory.getLogger(GenerationWsHandler.class);

  private final Map<String, Set<WebSocketSession>> sessionsByWorkflow = new ConcurrentHashMap<>();

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    String workflowId = workflowIdOf(session);
    if (workflowId == null) {
      session.close(CloseStatus.BAD_DATA);
      return;
    }
    sessionsByWorkflow.computeIfAbsent(workflowId, k -> ConcurrentHashMap.newKeySet()).add(session);
    log.debug("ws connected for generation {}", workflowId);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    String workflowId = workflowIdOf(session);
    if (workflowId == null) return;
    Set<WebSocketSession> set = sessionsByWorkflow.get(workflowId);
    if (set != null) {
      set.remove(session);
      if (set.isEmpty()) sessionsByWorkflow.remove(workflowId);
    }
  }

  /** Send a raw JSON payload to every browser watching {@code workflowId}. Best-effort. */
  public void broadcast(String workflowId, String payload) {
    Set<WebSocketSession> set = sessionsByWorkflow.get(workflowId);
    if (set == null || set.isEmpty()) return;
    TextMessage message = new TextMessage(payload);
    for (WebSocketSession session : set) {
      if (!session.isOpen()) continue;
      try {
        // WebSocketSession is not safe for concurrent sends; serialize per session.
        synchronized (session) {
          session.sendMessage(message);
        }
      } catch (IOException e) {
        log.debug("ws send failed for {}: {}", workflowId, e.getMessage());
      }
    }
  }

  /** Extract the trailing path segment of {@code /ws/generations/{workflowId}}. */
  private static String workflowIdOf(WebSocketSession session) {
    URI uri = session.getUri();
    if (uri == null) return null;
    String path = uri.getPath();
    int idx = path.lastIndexOf('/');
    if (idx < 0 || idx == path.length() - 1) return null;
    return path.substring(idx + 1);
  }
}
