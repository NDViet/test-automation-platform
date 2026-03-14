package com.platform.ingestion.security;

import com.platform.core.domain.ApiKey;
import com.platform.core.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Servlet filter that validates the {@code X-API-Key} header for all
 * non-excluded paths.
 *
 * <p>On success: sets a {@link UsernamePasswordAuthenticationToken} in the
 * security context and records {@link ApiKey#recordUsage()} (lazily persisted
 * by the {@link ApiKeyService}).</p>
 *
 * <p>On failure: writes HTTP 401 immediately without forwarding to the chain.</p>
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);
    static final String HEADER = "X-API-Key";

    private final ApiKeyRepository keyRepo;
    private final ApiKeyService keyService;

    public ApiKeyAuthFilter(ApiKeyRepository keyRepo, ApiKeyService keyService) {
        this.keyRepo    = keyRepo;
        this.keyService = keyService;
    }

    private static final List<String> PUBLIC_PATHS = List.of(
            "/actuator/health", "/actuator/info",
            "/swagger-ui", "/api-docs", "/v3/api-docs"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String rawKey = request.getHeader(HEADER);

        if (rawKey == null || rawKey.isBlank()) {
            rejectUnauthorized(response, "Missing X-API-Key header");
            return;
        }

        String hash = sha256hex(rawKey);
        Optional<ApiKey> found = keyRepo.findByKeyHash(hash);

        if (found.isEmpty() || !found.get().isActive()) {
            log.warn("[Security] Rejected request from {} — invalid or revoked API key prefix={}",
                    request.getRemoteAddr(), rawKey.length() >= 8 ? rawKey.substring(0, 8) : "?");
            rejectUnauthorized(response, "Invalid or revoked API key");
            return;
        }

        ApiKey key = found.get();
        keyService.recordUsage(key);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                key.getKeyPrefix(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_INGESTION")));
        auth.setDetails(key);
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(request, response);
    }

    private void rejectUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    private String sha256hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
