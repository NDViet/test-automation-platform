package com.platform.ingestion.security;

import com.platform.core.repository.ApiKeyRepository;
import com.platform.security.jwt.JwtService;
import com.platform.security.web.JwtCookieAuthFilter;
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
  private final JwtService jwtService;

  public SecurityConfig(ApiKeyRepository keyRepo, ApiKeyService keyService, JwtService jwtService) {
    this.keyRepo = keyRepo;
    this.keyService = keyService;
    this.jwtService = jwtService;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

    // The portal forwards the user's verified JWT as a Bearer token; this filter populates an
    // authenticated user principal so @RequireCapability can resolve the caller's roles. It runs
    // alongside the API-key filter (service-to-service calls) — whichever credential is present
    // authenticates the request; capability checks then no-op unless platform.security.enabled.
    http.addFilterBefore(
        new JwtCookieAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

    if (apiKeyEnabled) {
      http.authorizeHttpRequests(
              auth ->
                  auth
                      // Don't re-authorize error/forward/async dispatches — the JWT filter is
                      // skipped on the ERROR dispatch, so re-authorizing /error would mask the
                      // real upstream status as 403.
                      .dispatcherTypeMatchers(
                          jakarta.servlet.DispatcherType.ERROR,
                          jakarta.servlet.DispatcherType.FORWARD,
                          jakarta.servlet.DispatcherType.ASYNC)
                      .permitAll()
                      .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
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
