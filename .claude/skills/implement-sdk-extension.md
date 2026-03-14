# Skill: implement-sdk-extension

Implement a test framework SDK extension in `platform-sdk` that auto-publishes results to the platform after a test run completes.

## Context

- Module: `platform-sdk`
- Package: `com.platform.sdk`
- Target frameworks: JUnit 5, TestNG, Cucumber
- Integration method: HTTP POST to `/api/v1/results/ingest` (multipart)
- Config: environment variables or `platform.properties` on classpath
- Design principle: zero test code changes required — extension plugs in via annotations or config

## Config Properties

```properties
# src/test/resources/platform.properties  (or environment variables)
platform.url=https://platform.yourorg.com
platform.api-key=${PLATFORM_API_KEY}
platform.team-id=my-team
platform.project-id=my-service
platform.environment=${TEST_ENV:local}
platform.enabled=${PLATFORM_ENABLED:true}   # set false to disable in local dev
```

## Instructions

### 1. Implement `PlatformConfig`
```java
// PlatformConfig.java — loaded once at SDK startup
public class PlatformConfig {

    private final String url;
    private final String apiKey;
    private final String teamId;
    private final String projectId;
    private final String environment;
    private final boolean enabled;

    public static PlatformConfig load() {
        // 1. Try environment variables first (CI/CD)
        // 2. Fall back to platform.properties on classpath
        // 3. If neither found and platform.enabled != false → log warning, disable SDK
        Properties props = loadProperties();
        return new PlatformConfig(
            resolve(props, "platform.url", "PLATFORM_URL"),
            resolve(props, "platform.api-key", "PLATFORM_API_KEY"),
            resolve(props, "platform.team-id", "PLATFORM_TEAM_ID"),
            resolve(props, "platform.project-id", "PLATFORM_PROJECT_ID"),
            resolve(props, "platform.environment", "TEST_ENV", "unknown"),
            Boolean.parseBoolean(resolve(props, "platform.enabled", "PLATFORM_ENABLED", "true"))
        );
    }

    private static String resolve(Properties props, String propKey, String envVar) {
        String value = System.getenv(envVar);
        if (value != null) return value;
        value = props.getProperty(propKey);
        if (value != null) return value;
        return null;   // caller validates required fields
    }
}
```

### 2. Implement `PlatformReporter` (shared HTTP publisher)
```java
// PlatformReporter.java — used by all framework extensions
public class PlatformReporter {

    private static final Logger log = LoggerFactory.getLogger(PlatformReporter.class);

    private final PlatformConfig config;
    private final HttpClient httpClient;

    public void publishResults(Path reportDir, SourceFormat format, String branch) {
        if (!config.isEnabled()) {
            log.debug("Platform SDK disabled — skipping result publication");
            return;
        }

        // Validate required config
        if (config.getUrl() == null || config.getApiKey() == null) {
            log.warn("Platform SDK: platform.url or platform.api-key not configured — skipping");
            return;
        }

        try {
            List<Path> files = collectReportFiles(reportDir, format);
            if (files.isEmpty()) {
                log.warn("Platform SDK: no report files found in {}", reportDir);
                return;
            }
            doPublish(files, format, branch);
        } catch (Exception e) {
            // NEVER throw from SDK — test execution must not be affected by platform issues
            log.warn("Platform SDK: failed to publish results (non-fatal)", e);
        }
    }

    private void doPublish(List<Path> files, SourceFormat format, String branch) throws Exception {
        // Build multipart form
        // POST to {config.url}/api/v1/results/ingest
        // Timeout: 30 seconds
        // Log success at INFO, failures at WARN
    }
}
```

**Critical rule: the SDK must NEVER throw or fail the test run.** All exceptions are caught and logged at WARN.

### 3. Implement JUnit 5 extension
```java
// junit5/PlatformReportingExtension.java
public class PlatformReportingExtension implements AfterAllCallback {

    private static final PlatformConfig CONFIG = PlatformConfig.load();
    private static final PlatformReporter REPORTER = new PlatformReporter(CONFIG);

    @Override
    public void afterAll(ExtensionContext context) {
        // Only publish from the root test class (not nested classes)
        if (context.getParent().map(p -> p.getRoot() == p).orElse(true)) {
            PlatformProject annotation = context.getRequiredTestClass()
                .getAnnotation(PlatformProject.class);

            String branch = System.getenv().getOrDefault("GIT_BRANCH",
                System.getenv().getOrDefault("GITHUB_REF_NAME", "unknown"));

            // Surefire writes JUnit XML to target/surefire-reports/ by default
            Path reportsDir = Path.of("target", "surefire-reports");
            REPORTER.publishResults(reportsDir, SourceFormat.JUNIT_XML, branch);
        }
    }
}

// PlatformProject.java — team annotation
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PlatformProject {
    String team() default "";      // overrides platform.team-id property
    String project() default "";   // overrides platform.project-id property
}
```

Usage:
```java
@ExtendWith(PlatformReportingExtension.class)
@PlatformProject(team = "payments", project = "payment-service")
class PaymentFlowTest { }
```

### 4. Implement TestNG listener
```java
// testng/PlatformTestNGListener.java
public class PlatformTestNGListener implements IReporter {

    private static final PlatformReporter REPORTER = new PlatformReporter(PlatformConfig.load());

    @Override
    public void generateReport(
            List<XmlSuite> xmlSuites,
            List<ISuite> suites,
            String outputDirectory) {

        // TestNG writes results to outputDirectory/testng-results.xml
        Path reportsDir = Path.of(outputDirectory);
        String branch = System.getenv().getOrDefault("GIT_BRANCH", "unknown");
        REPORTER.publishResults(reportsDir, SourceFormat.TESTNG, branch);
    }
}
```

Usage in `testng.xml`:
```xml
<listeners>
  <listener class-name="com.platform.sdk.testng.PlatformTestNGListener"/>
</listeners>
```

### 5. Implement Cucumber plugin
```java
// cucumber/PlatformCucumberPlugin.java
public class PlatformCucumberPlugin implements ConcurrentEventListener {

    private static final PlatformReporter REPORTER = new PlatformReporter(PlatformConfig.load());

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestRunFinished.class, this::onTestRunFinished);
    }

    private void onTestRunFinished(TestRunFinished event) {
        // Cucumber writes cucumber.json to target/cucumber-reports/ when json plugin is active
        Path reportsDir = Path.of("target", "cucumber-reports");
        String branch = System.getenv().getOrDefault("GIT_BRANCH", "unknown");
        REPORTER.publishResults(reportsDir, SourceFormat.CUCUMBER_JSON, branch);
    }
}
```

Usage in `@CucumberOptions`:
```java
@CucumberOptions(
    plugin = {
        "json:target/cucumber-reports/cucumber.json",        // required for SDK to read
        "com.platform.sdk.cucumber.PlatformCucumberPlugin"   // SDK plugin
    }
)
```

### 6. Write SDK unit tests
```java
class PlatformReporterTest {

    @Test
    void shouldNotThrowWhenPlatformUrlNotConfigured() {
        var config = PlatformConfig.builder().enabled(true).url(null).build();
        var reporter = new PlatformReporter(config);

        // Must not throw
        assertThatCode(() -> reporter.publishResults(
            Path.of("target/surefire-reports"), JUNIT_XML, "main"))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldNotThrowWhenReportDirEmpty() {
        var reporter = new PlatformReporter(buildValidConfig());
        assertThatCode(() -> reporter.publishResults(
            Path.of("nonexistent/dir"), JUNIT_XML, "main"))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldSkipPublicationWhenDisabled() {
        var config = PlatformConfig.builder().enabled(false).build();
        var reporter = spy(new PlatformReporter(config));

        reporter.publishResults(Path.of("target"), JUNIT_XML, "main");

        verify(reporter, never()).doPublish(any(), any(), any());
    }
}
```

## Validation
- Extension/listener/plugin never throws — exceptions always caught internally
- SDK works with zero code changes to existing test classes (only `@ExtendWith` or listener config)
- `platform.enabled=false` prevents any HTTP calls — safe for local dev
- Missing `platform.url` or `platform.api-key` logs a warning, does not fail
- JUnit 5 extension publishes only once (from root class, not for each nested test class)
- All three framework adapters delegate to the same `PlatformReporter` — no duplication
