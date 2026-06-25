package com.platform.ingestion.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Manual Flyway configuration for Spring Boot 4.x. Spring Boot 4.x removed FlywayAutoConfiguration
 * from the core autoconfigure module; migrations must be configured explicitly.
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
