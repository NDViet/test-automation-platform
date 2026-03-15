package com.platform.portal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${portal.services.ingestion}")
    private String ingestionUrl;

    @Value("${portal.services.analytics}")
    private String analyticsUrl;

    @Value("${portal.services.ai}")
    private String aiUrl;

    @Value("${portal.services.integration}")
    private String integrationUrl;

    @Bean("ingestionClient")
    public RestClient ingestionClient() {
        return RestClient.builder().baseUrl(ingestionUrl).build();
    }

    @Bean("analyticsClient")
    public RestClient analyticsClient() {
        return RestClient.builder().baseUrl(analyticsUrl).build();
    }

    @Bean("aiClient")
    public RestClient aiClient() {
        return RestClient.builder().baseUrl(aiUrl).build();
    }

    @Bean("integrationClient")
    public RestClient integrationClient() {
        return RestClient.builder().baseUrl(integrationUrl).build();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Allow Vite dev server to call the BFF during development
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173", "http://localhost:3000")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource resource = super.getResource(resourcePath, location);
                        // If the resource exists (JS, CSS, images, etc.) serve it directly.
                        // Otherwise fall back to index.html so React Router handles the route.
                        if (resource != null && resource.exists()) {
                            return resource;
                        }
                        return location.createRelative("index.html");
                    }
                });
    }
}
