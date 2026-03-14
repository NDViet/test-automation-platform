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
 * <p>Authentication flow:</p>
 * <ol>
 *   <li>Client sends {@code X-API-Key: plat_...} header</li>
 *   <li>{@link ApiKeyAuthFilter} hashes the key and looks it up in the DB</li>
 *   <li>If valid and active, the request is authenticated with {@code ROLE_INGESTION}</li>
 *   <li>Otherwise, HTTP 401 is returned immediately</li>
 * </ol>
 *
 * <p>Set {@code platform.security.api-key.enabled=false} to disable in local dev.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${platform.security.api-key.enabled:true}")
    private boolean apiKeyEnabled;

    private final ApiKeyRepository keyRepo;
    private final ApiKeyService keyService;

    public SecurityConfig(ApiKeyRepository keyRepo, ApiKeyService keyService) {
        this.keyRepo    = keyRepo;
        this.keyService = keyService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Infrastructure endpoints — always open
                .requestMatchers(
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/api-docs/**",
                        "/v3/api-docs/**"
                ).permitAll()
                // Read-only query endpoints for the portal — no auth required
                .requestMatchers(
                        "/api/v1/teams/**",
                        "/api/v1/projects/**",
                        "/api/v1/executions/**"
                ).permitAll()
                // Coverage manifest — requires auth (teams must have an API key)
                .requestMatchers("/api/v1/coverage").authenticated()
                // API key management endpoints — require auth
                .requestMatchers("/api/v1/api-keys/**").authenticated()
                // All other requests — require auth (or open if disabled)
                .anyRequest().access((authSupplier, ctx) -> {
                    if (!apiKeyEnabled) {
                        return new org.springframework.security.authorization.AuthorizationDecision(true);
                    }
                    return new org.springframework.security.authorization.AuthorizationDecision(
                            authSupplier.get().isAuthenticated());
                })
            );

        if (apiKeyEnabled) {
            http.addFilterBefore(
                    new ApiKeyAuthFilter(keyRepo, keyService),
                    UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }
}
