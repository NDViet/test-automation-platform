package com.platform.portal.config;

import java.io.IOException;
import java.net.http.HttpClient;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Value("${portal.services.ingestion}")
  private String ingestionUrl;

  @Value("${portal.services.ingestion-service-key:}")
  private String ingestionServiceKey;

  @Value("${portal.services.analytics}")
  private String analyticsUrl;

  @Value("${portal.services.ai}")
  private String aiUrl;

  @Value("${portal.services.integration}")
  private String integrationUrl;

  @Value("${portal.services.agent:http://localhost:8086}")
  private String agentUrl;

  @Bean("ingestionClient")
  public RestClient ingestionClient() {
    RestClient.Builder builder =
        RestClient.builder().baseUrl(ingestionUrl).requestFactory(noVerifyRequestFactory());
    if (ingestionServiceKey != null && !ingestionServiceKey.isBlank()) {
      builder.defaultHeader("X-API-Key", ingestionServiceKey);
    }
    return builder.build();
  }

  @Bean("analyticsClient")
  public RestClient analyticsClient() {
    return RestClient.builder()
        .baseUrl(analyticsUrl)
        .requestFactory(noVerifyRequestFactory())
        .build();
  }

  @Bean("aiClient")
  public RestClient aiClient() {
    return RestClient.builder().baseUrl(aiUrl).requestFactory(noVerifyRequestFactory()).build();
  }

  @Bean("integrationClient")
  public RestClient integrationClient() {
    return RestClient.builder()
        .baseUrl(integrationUrl)
        .requestFactory(noVerifyRequestFactory())
        .build();
  }

  @Bean("agentClient")
  public RestClient agentClient() {
    return RestClient.builder().baseUrl(agentUrl).requestFactory(noVerifyRequestFactory()).build();
  }

  /**
   * Returns a request factory backed by a JDK HttpClient that skips TLS certificate and hostname
   * verification. Used for portal → backend service calls so they work even when backend services
   * use self-signed certs.
   *
   * <p>This is intentional for internal service-to-service calls within the platform. Do not use
   * for requests to external (public) endpoints.
   */
  private static JdkClientHttpRequestFactory noVerifyRequestFactory() {
    try {
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(
          null,
          new TrustManager[] {
            new X509TrustManager() {
              public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
              }

              public void checkClientTrusted(X509Certificate[] c, String a) {}

              public void checkServerTrusted(X509Certificate[] c, String a) {}
            }
          },
          null);

      HttpClient httpClient =
          HttpClient.newBuilder()
              .sslContext(sslContext)
              // Skip hostname verification — internal IPs won't match cert CN/SANs
              // when services haven't registered their own certs.
              .build();

      return new JdkClientHttpRequestFactory(httpClient);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create no-verify HTTP client", e);
    }
  }

  /**
   * Base dev origins always permitted (Vite dev server variants). Additional origins can be
   * injected at deploy time via the CORS_ALLOWED_ORIGINS env var (comma-separated):
   * CORS_ALLOWED_ORIGINS=http://192.168.2.10:5173,https://portal.example.com
   *
   * <p>Use a trailing wildcard pattern instead of a literal origin when the port is variable:
   * CORS_ALLOWED_ORIGIN_PATTERNS=http://192.168.2.*:*
   */
  @Value("${portal.cors.allowed-origins:}")
  private String extraOriginsRaw;

  @Value("${portal.cors.allowed-origin-patterns:}")
  private String extraPatternsRaw;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    List<String> origins =
        new ArrayList<>(List.of("http://localhost:5173", "http://localhost:3000"));

    if (StringUtils.hasText(extraOriginsRaw)) {
      Arrays.stream(extraOriginsRaw.split(","))
          .map(String::trim)
          .filter(StringUtils::hasText)
          .forEach(origins::add);
    }

    var mapping =
        registry
            .addMapping("/api/**")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .allowedOrigins(origins.toArray(String[]::new));

    // allowedOriginPatterns supports wildcards and is compatible with allowCredentials(true)
    if (StringUtils.hasText(extraPatternsRaw)) {
      String[] patterns =
          Arrays.stream(extraPatternsRaw.split(","))
              .map(String::trim)
              .filter(StringUtils::hasText)
              .toArray(String[]::new);
      if (patterns.length > 0) {
        mapping.allowedOriginPatterns(patterns);
      }
    }
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/**")
        .addResourceLocations("classpath:/static/")
        .resourceChain(true)
        .addResolver(
            new PathResourceResolver() {
              @Override
              protected Resource getResource(String resourcePath, Resource location)
                  throws IOException {
                Resource resource = super.getResource(resourcePath, location);
                // If the resource exists (JS, CSS, images, etc.) serve it directly.
                // Otherwise fall back to index.html so React Router handles the route.
                if (resource != null && resource.exists()) {
                  return resource;
                }
                return location.createRelative("index.html");
              }
            });
  }
}
