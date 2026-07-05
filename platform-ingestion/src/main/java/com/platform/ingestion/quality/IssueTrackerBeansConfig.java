package com.platform.ingestion.quality;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Registers just the issue-tracker factory beans from {@code platform-integration} so the
 * failure-cluster "create defect" flow can resolve a tracker on demand.
 *
 * <p>Ingestion's main app only scans {@code com.platform.ingestion} + {@code com.platform.core}, so
 * pulling in {@code platform-integration} does NOT auto-register its Kafka consumer / lifecycle
 * worker. We opt in to exactly the tracker packages here — the factories depend only on {@code
 * ObjectMapper}, so this stays lightweight and side-effect free.
 */
@Configuration
@ComponentScan(
    basePackages = {
      "com.platform.integration.port",
      "com.platform.integration.azureboards",
      "com.platform.integration.github",
      "com.platform.integration.jira"
    })
public class IssueTrackerBeansConfig {}
