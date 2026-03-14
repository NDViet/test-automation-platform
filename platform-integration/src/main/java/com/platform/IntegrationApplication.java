package com.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Platform Integration service — bidirectional JIRA ticket lifecycle management.
 *
 * <p>Package {@code com.platform} ensures Spring Boot scans both
 * {@code com.platform.integration.*} and {@code com.platform.core.*}.</p>
 */
@SpringBootApplication
public class IntegrationApplication {
    public static void main(String[] args) {
        SpringApplication.run(IntegrationApplication.class, args);
    }
}
