package com.platform.portal.auth;

import com.platform.security.web.JwtCookieAuthFilter;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Forwards the browser's {@code platform_token} cookie as an {@code Authorization: Bearer} header
 * on portal→service calls, so each backend service can authenticate the user from the verified JWT
 * (not a trusted header). Applied to every backend {@code RestClient}.
 */
@Component
public class JwtForwardingInterceptor implements ClientHttpRequestInterceptor {

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    if (request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION) == null) {
      String token = currentToken();
      if (token != null) {
        request.getHeaders().setBearerAuth(token);
      }
    }
    return execution.execute(request, body);
  }

  private static String currentToken() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
      Cookie[] cookies = attrs.getRequest().getCookies();
      if (cookies != null) {
        for (Cookie c : cookies) {
          if (JwtCookieAuthFilter.COOKIE_NAME.equals(c.getName())) {
            return c.getValue();
          }
        }
      }
    }
    return null;
  }
}
