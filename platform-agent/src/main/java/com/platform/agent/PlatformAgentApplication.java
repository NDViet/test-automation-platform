package com.platform.agent;

import com.platform.core.CoreConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.platform.agent", "com.platform.storage"})
@EntityScan(basePackages = "com.platform.core.domain")
@Import(CoreConfiguration.class)
@EnableKafka
@EnableAsync
@EnableScheduling
public class PlatformAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(PlatformAgentApplication.class, args);
    }
}
