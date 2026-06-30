package com.platform.portal.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.core.domain.User;
import com.platform.core.repository.UserRepository;
import com.platform.core.repository.UserRoleRepository;
import com.platform.portal.auth.AuthDtos.ChangePasswordRequest;
import com.platform.portal.auth.AuthDtos.LoginRequest;
import com.platform.portal.auth.AuthDtos.MeResponse;
import com.platform.security.jwt.JwtService;
import com.platform.security.web.JwtCookieAuthFilter;
import jakarta.servlet.http.Cookie;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

  @Mock UserRepository userRepo;
  @Mock UserRoleRepository roleRepo;

  final PasswordEncoder encoder = new BCryptPasswordEncoder();
  final JwtService jwt = new JwtService("a-test-signing-secret", 3600);
  AuthController controller;

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    controller = new AuthController(userRepo, roleRepo, encoder, jwt, false);
  }

  private User userWithId(
      UUID id, String username, String rawPassword, boolean superAdmin, boolean mustChange) {
    User u =
        new User(username, null, encoder.encode(rawPassword), "Display", superAdmin, mustChange);
    try {
      Field f = User.class.getDeclaredField("id");
      f.setAccessible(true);
      f.set(u, id);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
    return u;
  }

  @Test
  void loginSuccessSetsCookieAndReturnsUser() {
    UUID id = UUID.randomUUID();
    when(userRepo.findByUsername("admin"))
        .thenReturn(Optional.of(userWithId(id, "admin", "secretpw", true, true)));
    when(roleRepo.findByUserId(id)).thenReturn(List.of());
    MockHttpServletResponse res = new MockHttpServletResponse();

    MeResponse me = controller.login(new LoginRequest("admin", "secretpw"), res);

    assertThat(me.username()).isEqualTo("admin");
    assertThat(me.superAdmin()).isTrue();
    assertThat(me.mustChangePassword()).isTrue();
    String setCookie = res.getHeader(HttpHeaders.SET_COOKIE);
    assertThat(setCookie).contains(JwtCookieAuthFilter.COOKIE_NAME).contains("HttpOnly");
    verify(userRepo).save(any(User.class)); // last_login recorded
  }

  @Test
  void loginBadPasswordRejected() {
    when(userRepo.findByUsername("admin"))
        .thenReturn(Optional.of(userWithId(UUID.randomUUID(), "admin", "secretpw", false, false)));

    assertThatThrownBy(
            () ->
                controller.login(new LoginRequest("admin", "wrong"), new MockHttpServletResponse()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Invalid username or password");
  }

  @Test
  void loginUnknownUserRejected() {
    when(userRepo.findByUsername("ghost")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> controller.login(new LoginRequest("ghost", "x"), new MockHttpServletResponse()))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void meWithValidCookieReturnsUser() {
    UUID id = UUID.randomUUID();
    User u = userWithId(id, "alice", "pw", false, false);
    when(userRepo.findById(id)).thenReturn(Optional.of(u));
    when(roleRepo.findByUserId(id)).thenReturn(List.of());
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setCookies(new Cookie(JwtCookieAuthFilter.COOKIE_NAME, jwt.issue(id, "alice", false)));

    MeResponse me = controller.me(req);

    assertThat(me.username()).isEqualTo("alice");
  }

  @Test
  void meWithoutCookieRejected() {
    assertThatThrownBy(() -> controller.me(new MockHttpServletRequest()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Not authenticated");
  }

  @Test
  void changePasswordClearsMustChange() {
    UUID id = UUID.randomUUID();
    User u = userWithId(id, "admin", "oldpassw", true, true);
    when(userRepo.findById(id)).thenReturn(Optional.of(u));
    when(roleRepo.findByUserId(id)).thenReturn(List.of());
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setCookies(new Cookie(JwtCookieAuthFilter.COOKIE_NAME, jwt.issue(id, "admin", true)));

    MeResponse me =
        controller.changePassword(
            new ChangePasswordRequest("oldpassw", "newpassword"),
            req,
            new MockHttpServletResponse());

    assertThat(me.mustChangePassword()).isFalse();
    assertThat(encoder.matches("newpassword", u.getPasswordHash())).isTrue();
    verify(userRepo).save(u);
  }

  @Test
  void changePasswordWrongCurrentRejected() {
    UUID id = UUID.randomUUID();
    User u = userWithId(id, "admin", "oldpassw", true, true);
    when(userRepo.findById(id)).thenReturn(Optional.of(u));
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setCookies(new Cookie(JwtCookieAuthFilter.COOKIE_NAME, jwt.issue(id, "admin", true)));

    assertThatThrownBy(
            () ->
                controller.changePassword(
                    new ChangePasswordRequest("wrong", "newpassword"),
                    req,
                    new MockHttpServletResponse()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Current password is incorrect");
  }
}
