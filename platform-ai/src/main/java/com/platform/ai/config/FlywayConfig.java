package com.platform.ai.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit Flyway configuration required for Spring Boot 4.x. FlywayAutoConfiguration is not always
 * reachable from the module path in Spring Boot 4.x, so migrations are configured explicitly here.
 */
@Configuration
public class FlywayConfig {

  @Bean(initMethod = "migrate")
  public Flyway flyway(DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .baselineOnMigrate(false)
        .load();
  }
}
