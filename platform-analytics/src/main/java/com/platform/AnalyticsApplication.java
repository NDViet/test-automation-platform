package com.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Platform Analytics service — flakiness scoring, trends, quality gates, alerting.
 *
 * <p>Package {@code com.platform} is intentional: it ensures Spring Boot's component
 * and entity scanning covers both {@code com.platform.analytics.*} (this module's beans)
 * and {@code com.platform.core.*} (shared domain entities and repositories).</p>
 */
@SpringBootApplication
public class AnalyticsApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnalyticsApplication.class, args);
    }
}
