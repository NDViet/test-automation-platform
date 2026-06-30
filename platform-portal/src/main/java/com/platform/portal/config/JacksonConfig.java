package com.platform.portal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ensures an injectable {@link ObjectMapper} bean exists in the portal context.
 *
 * <p>The portal imports platform-core for auth, which also component-scans core's services
 * (CredentialResolver, etc.) — those inject an {@code ObjectMapper}. Spring MVC builds its own
 * mapper for HTTP message conversion but does not always expose a standalone {@code ObjectMapper}
 * bean here, so we register one explicitly (with module discovery for {@code java.time} types).
 * {@code @ConditionalOnMissingBean} keeps Boot's autoconfigured mapper as the winner when present.
 */
@Configuration
public class JacksonConfig {

  @Bean
  @ConditionalOnMissingBean
  public ObjectMapper objectMapper() {
    return new ObjectMapper().findAndRegisterModules();
  }
}
