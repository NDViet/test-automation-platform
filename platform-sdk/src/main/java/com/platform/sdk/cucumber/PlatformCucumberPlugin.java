package com.platform.sdk.cucumber;

import com.platform.sdk.config.PlatformConfig;
import com.platform.sdk.publisher.PlatformReporter;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.TestRunFinished;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Cucumber plugin that publishes JSON reports to the platform after the test run.
 *
 * <p>Register alongside the standard JSON formatter in your runner or CLI options:</p>
 * <pre>{@code
 * @CucumberOptions(plugin = {
 *     "json:target/cucumber-reports/cucumber.json",
 *     "com.platform.sdk.cucumber.PlatformCucumberPlugin"
 * })
 * }</pre>
 *
 * <p>The plugin reads JSON files from {@code target/cucumber-reports} (or the directory
 * specified by {@code PLATFORM_REPORT_DIR}) after the run completes.</p>
 */
public class PlatformCucumberPlugin implements ConcurrentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PlatformCucumberPlugin.class);

    private static final String FORMAT    = "CUCUMBER_JSON";
    private static final String FILE_GLOB = "*.json";
    private static final String DEFAULT_REPORT_DIR = "target/cucumber-reports";

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestRunFinished.class, event -> publish());
    }

    private void publish() {
        PlatformConfig config = PlatformConfig.load();

        String reportDirEnv = System.getenv("PLATFORM_REPORT_DIR");
        Path reportDir = Paths.get(reportDirEnv != null && !reportDirEnv.isBlank()
                ? reportDirEnv : DEFAULT_REPORT_DIR);

        String branch = resolveBranch();

        PlatformReporter reporter = new PlatformReporter(config);
        reporter.publishResults(reportDir, FORMAT, FILE_GLOB, branch);
    }

    private String resolveBranch() {
        String branch = System.getenv("GITHUB_REF_NAME");
        if (branch != null && !branch.isBlank()) return branch;
        branch = System.getenv("CI_COMMIT_REF_NAME");
        if (branch != null && !branch.isBlank()) return branch;
        branch = System.getenv("GIT_BRANCH");
        if (branch != null && !branch.isBlank()) return branch;
        return System.getenv("BRANCH_NAME");
    }
}
