package com.platform.security.web;

import com.platform.security.jwt.JwtService;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Wires the shared security model into a host service: scans the {@code com.platform.security} beans
 * (JwtService, evaluator, enforcer, aspect) and installs a stateless filter chain with the JWT auth
 * filter. Enforcement is on by default ({@code platform.security.enabled=true}); set it to false to
 * permit every request (auth still parsed if present) — only for break-glass/local debugging, not a
 * supported production mode. Services opt in with {@code @Import(PlatformSecurityConfiguration.class)}.
 */
@Configuration
@EnableWebSecurity
@EnableAspectJAutoProxy
@ComponentScan(basePackages = "com.platform.security")
public class PlatformSecurityConfiguration {

  @Bean
  SecurityFilterChain platformSecurityFilterChain(
      HttpSecurity http,
      JwtService jwt,
      @Value("${platform.security.enabled:true}") boolean enabled)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(
            new JwtCookieAuthFilter(jwt), UsernamePasswordAuthenticationFilter.class);

    if (enabled) {
      http.authorizeHttpRequests(
          auth ->
              auth
                  // Error/forward/async re-dispatches must not be re-authorized: the JWT filter
                  // (a OncePerRequestFilter) is skipped on the ERROR dispatch, so re-authorizing
                  // /error would deny it and mask the real status (e.g. an upstream 404) as 403.
                  .dispatcherTypeMatchers(
                      DispatcherType.ERROR, DispatcherType.FORWARD, DispatcherType.ASYNC)
                  .permitAll()
                  .requestMatchers(
                      "/actuator/health", "/actuator/health/**", "/actuator/info")
                  .permitAll()
                  .requestMatchers("/api/portal/auth/login")
                  .permitAll()
                  // Protect the API + websocket hub; everything else is the public SPA shell
                  // (index.html, JS/CSS assets, client routes). Auth happens via the API, so the
                  // static app must load for an unauthenticated user to reach the login page.
                  .requestMatchers("/api/**", "/hub/**")
                  .authenticated()
                  .anyRequest()
                  .permitAll());
    } else {
      http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    }
    return http.build();
  }
}
