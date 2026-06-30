package com.platform.security.web;

import com.platform.security.jwt.AuthenticatedUser;
import com.platform.security.jwt.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a request from the {@code platform_token} cookie (browser) or an
 * {@code Authorization: Bearer} header (service-to-service via the portal). A valid token sets the
 * {@link AuthenticatedUser} principal; an invalid/absent token leaves the context unauthenticated
 * (the filter chain then returns 401 for protected endpoints). Identity only — never trusts headers.
 */
public class JwtCookieAuthFilter extends OncePerRequestFilter {

  public static final String COOKIE_NAME = "platform_token";

  private final JwtService jwt;

  public JwtCookieAuthFilter(JwtService jwt) {
    this.jwt = jwt;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String token = extractToken(request);
    if (token != null) {
      try {
        AuthenticatedUser user = jwt.verify(token);
        var auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
      } catch (Exception e) {
        SecurityContextHolder.clearContext(); // invalid/expired ⇒ unauthenticated
      }
    }
    chain.doFilter(request, response);
  }

  private static String extractToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      return header.substring(7);
    }
    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
      for (Cookie c : cookies) {
        if (COOKIE_NAME.equals(c.getName())) {
          return c.getValue();
        }
      }
    }
    return null;
  }
}
