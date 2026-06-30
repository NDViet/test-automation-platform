package com.platform.security.web;

import com.platform.security.jwt.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Accessor for the authenticated principal set by {@link JwtCookieAuthFilter}. */
public final class CurrentUser {

  private CurrentUser() {}

  /** The current {@link AuthenticatedUser}, or {@code null} if the request is unauthenticated. */
  public static AuthenticatedUser get() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser u) {
      return u;
    }
    return null;
  }

  /**
   * The current user's username for audit fields (e.g. {@code created_by}), or {@code null} when
   * unauthenticated. Replaces the old {@code X-Actor} header — actor identity comes from the verified
   * JWT, never a client-supplied header.
   */
  public static String username() {
    AuthenticatedUser u = get();
    return u != null ? u.username() : null;
  }
}
