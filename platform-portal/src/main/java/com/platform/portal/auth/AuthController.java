package com.platform.portal.auth;

import com.platform.core.domain.User;
import com.platform.core.repository.UserRepository;
import com.platform.core.repository.UserRoleRepository;
import com.platform.portal.auth.AuthDtos.ChangePasswordRequest;
import com.platform.portal.auth.AuthDtos.LoginRequest;
import com.platform.portal.auth.AuthDtos.MeResponse;
import com.platform.portal.auth.AuthDtos.RoleGrant;
import com.platform.security.jwt.AuthenticatedUser;
import com.platform.security.jwt.JwtService;
import com.platform.security.web.JwtCookieAuthFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Authentication endpoints. Login verifies a BCrypt password and sets an httpOnly JWT cookie; the
 * token carries identity only (roles are resolved live). The cookie is the session — no token is
 * returned in the body and passwords are never echoed.
 */
@RestController
@RequestMapping("/api/portal/auth")
public class AuthController {

  private final UserRepository userRepo;
  private final UserRoleRepository roleRepo;
  private final PasswordEncoder encoder;
  private final JwtService jwt;
  private final boolean secureCookie;

  public AuthController(
      UserRepository userRepo,
      UserRoleRepository roleRepo,
      PasswordEncoder encoder,
      JwtService jwt,
      @Value("${platform.security.cookie-secure:false}") boolean secureCookie) {
    this.userRepo = userRepo;
    this.roleRepo = roleRepo;
    this.encoder = encoder;
    this.jwt = jwt;
    this.secureCookie = secureCookie;
  }

  @PostMapping("/login")
  public MeResponse login(@RequestBody LoginRequest req, HttpServletResponse res) {
    String username = req.username() == null ? "" : req.username().trim();
    User user =
        userRepo
            .findByUsername(username)
            .filter(User::isEnabled)
            .filter(u -> encoder.matches(nullToEmpty(req.password()), u.getPasswordHash()))
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid username or password"));
    user.recordLogin();
    userRepo.save(user);
    setTokenCookie(res, user);
    return me(user);
  }

  @PostMapping("/logout")
  public Map<String, Object> logout(HttpServletResponse res) {
    clearTokenCookie(res);
    return Map.of("status", "ok");
  }

  @GetMapping("/me")
  public MeResponse me(HttpServletRequest req) {
    User user = currentUser(req);
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
    }
    return me(user);
  }

  @PostMapping("/change-password")
  public MeResponse changePassword(
      @RequestBody ChangePasswordRequest req,
      HttpServletRequest request,
      HttpServletResponse res) {
    User user = currentUser(request);
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
    }
    if (!encoder.matches(nullToEmpty(req.currentPassword()), user.getPasswordHash())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
    }
    String newPassword = req.newPassword() == null ? "" : req.newPassword();
    if (newPassword.length() < 8) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "New password must be at least 8 characters");
    }
    user.changePassword(encoder.encode(newPassword));
    userRepo.save(user);
    setTokenCookie(res, user); // refresh the session
    return me(user);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private User currentUser(HttpServletRequest req) {
    String token = cookieValue(req, JwtCookieAuthFilter.COOKIE_NAME);
    if (token == null) return null;
    try {
      AuthenticatedUser au = jwt.verify(token);
      return userRepo.findById(au.userId()).filter(User::isEnabled).orElse(null);
    } catch (Exception e) {
      return null;
    }
  }

  private MeResponse me(User user) {
    List<RoleGrant> roles =
        roleRepo.findByUserId(user.getId()).stream()
            .map(r -> new RoleGrant(r.getRole(), r.getScope(), r.getScopeId().toString()))
            .toList();
    return new MeResponse(
        user.getUsername(),
        user.getDisplayName(),
        user.isSuperAdmin(),
        user.isMustChangePassword(),
        roles);
  }

  private void setTokenCookie(HttpServletResponse res, User user) {
    String token = jwt.issue(user.getId(), user.getUsername(), user.isSuperAdmin());
    ResponseCookie cookie =
        ResponseCookie.from(JwtCookieAuthFilter.COOKIE_NAME, token)
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite("Lax")
            .path("/")
            .maxAge(jwt.ttlSeconds())
            .build();
    res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  private void clearTokenCookie(HttpServletResponse res) {
    ResponseCookie cookie =
        ResponseCookie.from(JwtCookieAuthFilter.COOKIE_NAME, "")
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite("Lax")
            .path("/")
            .maxAge(0)
            .build();
    res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  private static String cookieValue(HttpServletRequest req, String name) {
    Cookie[] cookies = req.getCookies();
    if (cookies != null) {
      for (Cookie c : cookies) {
        if (name.equals(c.getName())) return c.getValue();
      }
    }
    return null;
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
