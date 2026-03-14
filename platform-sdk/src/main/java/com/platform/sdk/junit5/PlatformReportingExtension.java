package com.platform.sdk.junit5;

import com.platform.sdk.annotation.PlatformProject;
import com.platform.sdk.config.PlatformConfig;
import com.platform.sdk.publisher.PlatformReporter;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * JUnit 5 extension that auto-publishes Surefire/Failsafe XML reports after all
 * tests in a class (or top-level suite) have run.
 *
 * <p>Register via {@code @ExtendWith(PlatformReportingExtension.class)} or
 * auto-register via {@code META-INF/services/org.junit.jupiter.api.extension.Extension}.</p>
 *
 * <p>Report directory defaults to {@code target/surefire-reports} but can be
 * overridden with the {@code PLATFORM_REPORT_DIR} environment variable.</p>
 */
public class PlatformReportingExtension implements AfterAllCallback {

    private static final Logger log = LoggerFactory.getLogger(PlatformReportingExtension.class);

    private static final String FORMAT     = "JUNIT_XML";
    private static final String FILE_GLOB  = "*.xml";
    private static final String DEFAULT_REPORT_DIR = "target/surefire-reports";

    @Override
    public void afterAll(ExtensionContext context) {
        // Only fire at the outermost class level (not for nested classes)
        if (context.getParent().map(p -> p.getParent().isPresent()).orElse(false)) {
            return;
        }

        PlatformConfig base = PlatformConfig.load();

        // Check for @PlatformProject on the test class
        PlatformConfig config = context.getTestClass()
                .filter(cls -> cls.isAnnotationPresent(PlatformProject.class))
                .map(cls -> {
                    PlatformProject ann = cls.getAnnotation(PlatformProject.class);
                    return base.withOverrides(ann.teamId(), ann.projectId());
                })
                .orElse(base);

        String reportDirEnv = System.getenv("PLATFORM_REPORT_DIR");
        Path reportDir = Paths.get(reportDirEnv != null && !reportDirEnv.isBlank()
                ? reportDirEnv : DEFAULT_REPORT_DIR);

        String branch = resolveBranch();

        PlatformReporter reporter = new PlatformReporter(config);
        reporter.publishResults(reportDir, FORMAT, FILE_GLOB, branch);
    }

    private String resolveBranch() {
        // GitHub Actions
        String branch = System.getenv("GITHUB_REF_NAME");
        if (branch != null && !branch.isBlank()) return branch;
        // GitLab CI
        branch = System.getenv("CI_COMMIT_REF_NAME");
        if (branch != null && !branch.isBlank()) return branch;
        // Jenkins
        branch = System.getenv("GIT_BRANCH");
        if (branch != null && !branch.isBlank()) return branch;
        // Generic fallback
        branch = System.getenv("BRANCH_NAME");
        return branch;
    }
}
