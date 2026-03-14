# platform-sdk — Usage Guide

`platform-sdk` is the zero-friction integration layer. It auto-publishes test results to the platform
**without requiring any changes to your test code**. Pick the adapter for your framework, drop in a
config file, and you're done.

---

## When to use platform-sdk vs platform-testframework

| | platform-sdk | platform-testframework |
|---|---|---|
| **What you get** | Auto-publish after the run | Rich step tracking, tracing, retry, Playwright support |
| **Code changes** | None (just register the adapter) | Extend base class / use annotations |
| **Best for** | Existing test suites you don't want to touch | New test suites or teams wanting deeper observability |

`platform-testframework` depends on `platform-sdk` — you never need both in the same pom.

---

## 1. Add the dependency

```xml
<dependency>
    <groupId>com.platform</groupId>
    <artifactId>platform-sdk</artifactId>
    <version>${project.version}</version>
    <scope>test</scope>
</dependency>
```

---

## 2. Configure the SDK

Create `src/test/resources/platform.properties`:

```properties
# Required
platform.url        = http://localhost:8081
platform.api-key    = plat_your_key_here
platform.team-id    = team-payments
platform.project-id = proj-checkout-e2e

# Optional (defaults shown)
platform.environment = unknown   # staging, production, local, etc.
platform.enabled     = true      # set false to disable without removing the dependency
```

All properties can be overridden by environment variables (useful in CI):

| Property | Environment variable | Default |
|---|---|---|
| `platform.url` | `PLATFORM_URL` | _(required)_ |
| `platform.api-key` | `PLATFORM_API_KEY` | _(required)_ |
| `platform.team-id` | `PLATFORM_TEAM_ID` | _(required)_ |
| `platform.project-id` | `PLATFORM_PROJECT_ID` | _(required)_ |
| `platform.environment` | `TEST_ENV` | `unknown` |
| `platform.enabled` | `PLATFORM_ENABLED` | `true` |

**Priority:** environment variable > `platform.properties` > default.

---

## 3. Register the adapter for your framework

### JUnit 5

Register via `@ExtendWith` on any test class, or globally via Surefire `extensions.autodetection`:

```java
// Option A: per-class
@ExtendWith(PlatformReportingExtension.class)
class MyTest {
    @Test void myTest() { ... }
}
```

```java
// Option B: global — add to src/test/resources/META-INF/services/
//           org.junit.jupiter.api.extension.Extension
//           (one class per line)
com.platform.sdk.junit5.PlatformReportingExtension
```

The extension fires after all tests in the outermost class have run, reads XML files from
`target/surefire-reports` (or `$PLATFORM_REPORT_DIR`), and uploads them as `JUNIT_XML`.

**Per-class project override** — if one test class belongs to a different team/project:

```java
@PlatformProject(teamId = "team-search", projectId = "proj-search-e2e")
@ExtendWith(PlatformReportingExtension.class)
class SearchTest { ... }
```

---

### TestNG

Register in `testng.xml`:

```xml
<listeners>
    <listener class-name="com.platform.sdk.testng.PlatformTestNGListener"/>
</listeners>
```

Or on a single test class:

```java
@Listeners(PlatformTestNGListener.class)
public class LoginTest { ... }
```

The listener implements `IReporter` — it fires after the suite, reads `testng-results.xml` from the
TestNG output directory (or `$PLATFORM_REPORT_DIR`), and uploads as `TESTNG`.

---

### Cucumber

Register alongside the standard JSON formatter in your `@CucumberOptions` or `cucumber.properties`:

```java
// @CucumberOptions
@CucumberOptions(plugin = {
    "json:target/cucumber-reports/cucumber.json",           // still needed for local HTML reports
    "com.platform.sdk.cucumber.PlatformCucumberPlugin"
})
public class RunnerClass {}
```

```properties
# src/test/resources/cucumber.properties (alternative)
cucumber.plugin=json:target/cucumber-reports/cucumber.json,\
                com.platform.sdk.cucumber.PlatformCucumberPlugin
```

The plugin listens for `TestRunFinished`, then reads `*.json` files from `target/cucumber-reports`
(or `$PLATFORM_REPORT_DIR`) and uploads them as `CUCUMBER_JSON`.

> **Tip:** if you use `platform-testframework`'s `PlatformCucumberPlugin` instead, you get native
> per-step result publishing — skip the JSON file entirely.

---

## 4. Report directory override

All adapters respect the `PLATFORM_REPORT_DIR` environment variable:

```bash
# CI — custom report location
PLATFORM_REPORT_DIR=/workspace/reports mvn test
```

Default directories per adapter:

| Adapter | Default report dir |
|---|---|
| JUnit 5 | `target/surefire-reports` |
| TestNG | TestNG `outputDirectory` → fallback `test-output` |
| Cucumber | `target/cucumber-reports` |

---

## 5. API reference

### `PlatformConfig`

Loaded automatically by all adapters. Only needed directly if calling `PlatformReporter` manually.

```java
PlatformConfig config = PlatformConfig.load();   // reads env vars + platform.properties

// Override team/project (used by @PlatformProject internally)
PlatformConfig scoped = config.withOverrides("team-search", "proj-search");

config.isEnabled()     // false if platform.enabled=false or url/apiKey missing
config.isValid()       // true only if all 4 required fields are present
config.getUrl()        // ingestion service base URL
config.getTeamId()     // team slug
config.getProjectId()  // project slug
config.getEnvironment()// test environment label
```

### `PlatformReporter`

The underlying HTTP publisher used by all adapters. Call directly only if building a custom adapter.

```java
PlatformReporter reporter = new PlatformReporter(config);

// Upload report files (JUnit XML, Cucumber JSON, TestNG XML)
reporter.publishResults(
    Path.of("target/surefire-reports"),  // directory containing files
    "JUNIT_XML",                          // format: JUNIT_XML | CUCUMBER_JSON | TESTNG | ALLURE | PLAYWRIGHT | NEWMAN
    "*.xml",                              // glob to match files
    "main"                                // branch name (from CI env, or null)
);

// Upload a natively-constructed UnifiedTestResult (used by platform-testframework)
reporter.publishNative(unifiedTestResult);
```

**Never throws.** All failures are logged at WARN and swallowed — the publisher will never cause
your test run to fail.

### `@PlatformProject`

Class-level annotation that overrides the `teamId` / `projectId` from `platform.properties`
for a specific test class. Only used with `PlatformReportingExtension` (JUnit 5).

```java
@PlatformProject(teamId = "team-payments", projectId = "proj-checkout")
class CheckoutTest { ... }
```

| Attribute | Type | Default | Description |
|---|---|---|---|
| `teamId` | `String` | `""` (use global) | Team slug override |
| `projectId` | `String` | `""` (use global) | Project slug override |

---

## 6. CI setup example (GitHub Actions)

```yaml
env:
  PLATFORM_URL:        ${{ secrets.PLATFORM_URL }}
  PLATFORM_API_KEY:    ${{ secrets.PLATFORM_API_KEY }}
  PLATFORM_TEAM_ID:    team-payments
  PLATFORM_PROJECT_ID: proj-checkout-e2e
  TEST_ENV:            staging

steps:
  - uses: actions/checkout@v4
  - uses: actions/setup-java@v4
    with: { java-version: '21', distribution: 'temurin' }
  - run: mvn test
```

No other changes needed — the adapter picks up the env vars and publishes automatically.

---

## 7. Disabling the SDK

Set `platform.enabled=false` in `platform.properties` (or `PLATFORM_ENABLED=false`) to skip
all publishing without removing the dependency. Useful for local runs that don't need to report.
