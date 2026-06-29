package com.platform.portal.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Exposes the live generation-progress WebSocket at {@code /ws/generations/{workflowId}}. This
 * handler mapping takes precedence over the SPA static-resource fallback, so the path is not
 * swallowed by index.html.
 */
@Configuration
@EnableWebSocket
public class GenerationWsConfig implements WebSocketConfigurer {

  private final GenerationWsHandler handler;

  public GenerationWsConfig(GenerationWsHandler handler) {
    this.handler = handler;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    // Origin patterns cover same-origin (prod) and the Vite dev server; tighten if needed.
    registry.addHandler(handler, "/ws/generations/*").setAllowedOriginPatterns("*");
  }
}
