package com.platform.ingestion.security;

import com.platform.security.authz.PermissionEvaluator;
import com.platform.security.authz.RoleResolver;
import com.platform.security.jwt.JwtService;
import com.platform.security.web.CapabilityEnforcer;
import com.platform.security.web.RequireCapabilityAspect;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

/**
 * Brings the platform-security capability machinery into platform-ingestion <em>without</em> its
 * {@code PlatformSecurityConfiguration} (which declares its own {@code @EnableWebSecurity}
 * SecurityFilterChain). Ingestion already owns a chain ({@link SecurityConfig}) for the API-key
 * path, so importing the full security config would create a conflicting second chain. Instead we
 * register only the beans the {@code @RequireCapability} aspect needs and let {@link SecurityConfig}
 * add the JWT filter into the existing chain — so both X-API-Key (services) and JWT (users)
 * authenticate side by side.
 */
@Configuration
@EnableAspectJAutoProxy
@Import({
  JwtService.class,
  RoleResolver.class,
  PermissionEvaluator.class,
  CapabilityEnforcer.class,
  RequireCapabilityAspect.class
})
public class IngestionRbacConfig {}
