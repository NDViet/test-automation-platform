package com.platform.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit Jackson configuration.
 *
 * <p>Spring Boot 4.x decomposes {@code spring-boot-autoconfigure} into separate
 * modules; {@code JacksonAutoConfiguration} is not always reachable from the
 * module path of this service, so we register the {@link ObjectMapper} bean
 * explicitly here.  {@code @ConditionalOnMissingBean} ensures that if the
 * auto-configuration does run first, this definition is skipped.</p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
