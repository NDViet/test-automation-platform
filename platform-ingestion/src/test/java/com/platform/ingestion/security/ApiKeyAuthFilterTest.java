package com.platform.ingestion.security;

import com.platform.core.domain.ApiKey;
import com.platform.core.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthFilterTest {

    @Mock ApiKeyRepository keyRepo;
    @Mock ApiKeyService keyService;
    @Mock FilterChain chain;

    ApiKeyAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthFilter(keyRepo, keyService);
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejects401WhenNoHeaderPresent() throws Exception {
        MockHttpServletRequest request  = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void rejects401WhenKeyNotFoundInDb() throws Exception {
        MockHttpServletRequest request  = new MockHttpServletRequest();
        request.addHeader(ApiKeyAuthFilter.HEADER, "plat_unknownkey");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(keyRepo.findByKeyHash(anyString())).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void rejects401WhenKeyIsRevoked() throws Exception {
        ApiKey revokedKey = ApiKey.builder()
                .name("test").keyHash("hash").keyPrefix("plat_test")
                .teamId(UUID.randomUUID()).build();
        revokedKey.revoke();

        MockHttpServletRequest request  = new MockHttpServletRequest();
        request.addHeader(ApiKeyAuthFilter.HEADER, "plat_validrawkey");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(keyRepo.findByKeyHash(anyString())).thenReturn(Optional.of(revokedKey));

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void allowsRequestAndSetsAuthWhenKeyIsValid() throws Exception {
        ApiKey activeKey = ApiKey.builder()
                .name("ci-key").keyHash("willbehashed").keyPrefix("plat_ci1")
                .teamId(UUID.randomUUID()).build();

        // Use a real raw key whose SHA-256 hash matches the mock lookup
        String rawKey = "plat_testvalidkey12345678";
        when(keyRepo.findByKeyHash(anyString())).thenReturn(Optional.of(activeKey));

        MockHttpServletRequest request  = new MockHttpServletRequest();
        request.addHeader(ApiKeyAuthFilter.HEADER, rawKey);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        verify(chain).doFilter(request, response);
        verify(keyService).recordUsage(activeKey);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
    }

    @Test
    void setsCorrectRoleOnAuthentication() throws Exception {
        ApiKey activeKey = ApiKey.builder()
                .name("role-test").keyHash("hash").keyPrefix("plat_rol")
                .teamId(UUID.randomUUID()).build();

        when(keyRepo.findByKeyHash(anyString())).thenReturn(Optional.of(activeKey));

        MockHttpServletRequest request  = new MockHttpServletRequest();
        request.addHeader(ApiKeyAuthFilter.HEADER, "plat_anyrawkeyvalue");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_INGESTION"));
    }
}
