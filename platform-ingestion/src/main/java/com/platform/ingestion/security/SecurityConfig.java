package com.platform.ingestion.security;

import com.platform.core.repository.ApiKeyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless API key security for platform-ingestion.
 *
 * <p>Authentication flow:
 *
 * <ol>
 *   <li>Client sends {@code X-API-Key: plat_...} header
 *   <li>{@link ApiKeyAuthFilter} hashes the key and looks it up in the DB
 *   <li>If valid and active, the request is authenticated with {@code ROLE_INGESTION}
 *   <li>Otherwise, HTTP 401 is returned immediately
 * </ol>
 *
 * <p>Set {@code platform.security.api-key.enabled=false} to disable in local dev.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Value("${platform.security.api-key.enabled:true}")
  private boolean apiKeyEnabled;

  @Value("${platform.portal.service-key:}")
  private String internalServiceKey;

  private final ApiKeyRepository keyRepo;
  private final ApiKeyService keyService;

  public SecurityConfig(ApiKeyRepository keyRepo, ApiKeyService keyService) {
    this.keyRepo = keyRepo;
    this.keyService = keyService;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

    if (apiKeyEnabled) {
      http.authorizeHttpRequests(
              auth ->
                  auth.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                      .permitAll()
                      .anyRequest()
                      .authenticated())
          .addFilterBefore(
              new ApiKeyAuthFilter(keyRepo, keyService, internalServiceKey),
              UsernamePasswordAuthenticationFilter.class);
    } else {
      // Auth disabled (local dev) — allow everything
      http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    }

    return http.build();
  }
}
