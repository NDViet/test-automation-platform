package com.platform.ingestion;

import com.platform.core.CoreConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = {"com.platform.ingestion", "com.platform.core"})
@Import(CoreConfiguration.class)
@EntityScan(basePackages = "com.platform.core.domain")
public class IngestionApplication {
    public static void main(String[] args) {
        SpringApplication.run(IngestionApplication.class, args);
    }
}
