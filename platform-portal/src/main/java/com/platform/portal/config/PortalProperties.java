package com.platform.portal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "portal.services")
public class PortalProperties {
    private String ingestion  = "http://localhost:8081";
    private String analytics  = "http://localhost:8082";
    private String integration = "http://localhost:8083";
    private String ai         = "http://localhost:8084";

    public String getIngestion() { return ingestion; }
    public void setIngestion(String v) { this.ingestion = v; }
    public String getAnalytics() { return analytics; }
    public void setAnalytics(String v) { this.analytics = v; }
    public String getIntegration() { return integration; }
    public void setIntegration(String v) { this.integration = v; }
    public String getAi() { return ai; }
    public void setAi(String v) { this.ai = v; }
}
