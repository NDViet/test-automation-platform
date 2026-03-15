package com.platform.sdk.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;

/**
 * Loads SDK configuration from environment variables (priority 1)
 * or {@code platform.properties} on the classpath (priority 2).
 */
public class PlatformConfig {

    private static final Logger log = LoggerFactory.getLogger(PlatformConfig.class);
    private static final String PROPS_FILE = "platform.properties";

    private final String url;
    private final String apiKey;
    private final String teamId;
    private final String projectId;
    private final String environment;
    private final boolean enabled;

    private PlatformConfig(String url, String apiKey, String teamId,
                            String projectId, String environment, boolean enabled) {
        this.url = url;
        this.apiKey = apiKey;
        this.teamId = teamId;
        this.projectId = projectId;
        this.environment = environment;
        this.enabled = enabled;
    }

    public static PlatformConfig load() {
        Properties props = loadPropertiesFile();

        boolean enabled = Boolean.parseBoolean(
                resolve(props, "platform.enabled", "PLATFORM_ENABLED", "true"));
        String url       = resolve(props, "platform.url",        "PLATFORM_URL",        null);
        String apiKey    = resolve(props, "platform.api-key",    "PLATFORM_API_KEY",    null);
        String teamId    = resolve(props, "platform.team-id",    "PLATFORM_TEAM_ID",    null);
        String projectId = resolve(props, "platform.project-id", "PLATFORM_PROJECT_ID", null);
        String env       = resolve(props, "platform.environment","TEST_ENV",            "unknown");

        if (enabled && (url == null || apiKey == null)) {
            log.warn("[Platform SDK] platform.url or platform.api-key not configured — SDK will be disabled");
            enabled = false;
        }

        return new PlatformConfig(url, apiKey, teamId, projectId, env, enabled);
    }

    private static Properties loadPropertiesFile() {
        Properties props = new Properties();
        try (InputStream is = PlatformConfig.class.getClassLoader()
                .getResourceAsStream(PROPS_FILE)) {
            if (is != null) {
                props.load(is);
            }
        } catch (Exception e) {
            log.debug("[Platform SDK] Could not load {}: {}", PROPS_FILE, e.getMessage());
        }
        return props;
    }

    private static String resolve(Properties props, String propKey, String envVar, String defaultValue) {
        String env = System.getenv(envVar);
        if (env != null && !env.isBlank()) return env;
        String prop = props.getProperty(propKey);
        if (prop != null && !prop.isBlank()) return prop;
        return defaultValue;
    }

    // Override team/project from annotation if provided
    public PlatformConfig withOverrides(String teamOverride, String projectOverride) {
        String effectiveTeam    = (teamOverride    != null && !teamOverride.isBlank())    ? teamOverride    : this.teamId;
        String effectiveProject = (projectOverride != null && !projectOverride.isBlank()) ? projectOverride : this.projectId;
        return new PlatformConfig(url, apiKey, effectiveTeam, effectiveProject, environment, enabled);
    }

    public String getUrl()         { return url; }
    public String getApiKey()      { return apiKey; }
    public String getTeamId()      { return teamId; }
    public String getProjectId()   { return projectId; }
    public String getEnvironment() { return environment; }
    public boolean isEnabled()     { return enabled; }

    public boolean isValid() {
        return enabled && url != null && apiKey != null
                && teamId != null && projectId != null;
    }
}
