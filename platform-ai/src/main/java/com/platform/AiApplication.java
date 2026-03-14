package com.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Platform AI service — Claude-powered failure classification and intelligence.
 *
 * <p>Package {@code com.platform} ensures Spring Boot scans both
 * {@code com.platform.ai.*} (this module's beans) and
 * {@code com.platform.core.*} (shared domain entities and repositories).</p>
 */
@SpringBootApplication
@EnableScheduling
public class AiApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }
}
