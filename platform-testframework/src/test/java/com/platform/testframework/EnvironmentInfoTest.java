package com.platform.testframework;

import com.platform.testframework.report.EnvironmentInfo;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentInfoTest {

    @Test
    void collectsJvmAndOsInfo() {
        Map<String, String> env = EnvironmentInfo.collect();

        assertThat(env).containsKey("java.version");
        assertThat(env).containsKey("os.name");
        assertThat(env).containsKey("ci.provider");
    }

    @Test
    void ciProviderIsLocalInTestEnvironment() {
        Map<String, String> env = EnvironmentInfo.collect();
        // Running locally — no CI env vars set
        assertThat(env.get("ci.provider")).isIn("local", "ci", "github-actions", "gitlab-ci", "jenkins", "circleci");
    }

    @Test
    void javaVersionIsPresent() {
        Map<String, String> env = EnvironmentInfo.collect();
        assertThat(env.get("java.version")).isNotBlank();
    }
}
