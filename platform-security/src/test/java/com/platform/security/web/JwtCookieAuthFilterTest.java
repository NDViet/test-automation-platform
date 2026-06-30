package com.platform.security.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.security.jwt.JwtService;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtCookieAuthFilterTest {

  private final JwtService jwt = new JwtService("a-test-signing-secret", 3600);
  private final JwtCookieAuthFilter filter = new JwtCookieAuthFilter(jwt);

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void authenticatesFromCookie() throws Exception {
    UUID id = UUID.randomUUID();
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setCookies(new Cookie(JwtCookieAuthFilter.COOKIE_NAME, jwt.issue(id, "alice", false)));

    filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

    assertThat(CurrentUser.get()).isNotNull();
    assertThat(CurrentUser.get().userId()).isEqualTo(id);
    assertThat(CurrentUser.get().username()).isEqualTo("alice");
  }

  @Test
  void authenticatesFromBearerHeader() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("Authorization", "Bearer " + jwt.issue(UUID.randomUUID(), "bob", true));

    filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

    assertThat(CurrentUser.get()).isNotNull();
    assertThat(CurrentUser.get().superAdmin()).isTrue();
  }

  @Test
  void invalidTokenLeavesUnauthenticated() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("Authorization", "Bearer not-a-real-token");

    filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

    assertThat(CurrentUser.get()).isNull();
  }

  @Test
  void noTokenLeavesUnauthenticated() throws Exception {
    filter.doFilter(
        new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());
    assertThat(CurrentUser.get()).isNull();
  }
}
