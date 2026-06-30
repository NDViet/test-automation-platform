package com.platform.portal;

import com.platform.core.CoreConfiguration;
import com.platform.security.web.PlatformSecurityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EntityScan(basePackages = "com.platform.core.domain")
@Import({CoreConfiguration.class, PlatformSecurityConfiguration.class})
public class PortalApplication {
  public static void main(String[] args) {
    SpringApplication.run(PortalApplication.class, args);
  }
}
