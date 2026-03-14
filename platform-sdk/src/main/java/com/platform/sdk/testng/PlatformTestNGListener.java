package com.platform.sdk.testng;

import com.platform.sdk.config.PlatformConfig;
import com.platform.sdk.publisher.PlatformReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IReporter;
import org.testng.ISuite;
import org.testng.xml.XmlSuite;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * TestNG listener that publishes {@code testng-results.xml} to the platform after
 * the suite finishes.
 *
 * <p>Register in your {@code testng.xml}:</p>
 * <pre>{@code
 * <listeners>
 *   <listener class-name="com.platform.sdk.testng.PlatformTestNGListener"/>
 * </listeners>
 * }</pre>
 *
 * <p>Or via {@code @Listeners(PlatformTestNGListener.class)} on any test class.</p>
 */
public class PlatformTestNGListener implements IReporter {

    private static final Logger log = LoggerFactory.getLogger(PlatformTestNGListener.class);

    private static final String FORMAT    = "TESTNG";
    private static final String FILE_GLOB = "testng-results.xml";
    private static final String DEFAULT_REPORT_DIR = "test-output";

    @Override
    public void generateReport(List<XmlSuite> xmlSuites, List<ISuite> suites, String outputDirectory) {
        PlatformConfig config = PlatformConfig.load();

        String reportDirEnv = System.getenv("PLATFORM_REPORT_DIR");
        Path reportDir = Paths.get(reportDirEnv != null && !reportDirEnv.isBlank()
                ? reportDirEnv : (outputDirectory != null ? outputDirectory : DEFAULT_REPORT_DIR));

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
