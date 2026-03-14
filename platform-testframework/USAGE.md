# platform-testframework — Usage Guide

`platform-testframework` is the full observability layer for new test suites. On top of
`platform-sdk`'s auto-publish, it adds:

- **Structured step tracking** — per-step timing and status published to the platform
- **Dual logging** — every log line goes to SLF4J (Logstash/OpenSearch) *and* the test result
- **OpenTelemetry tracing** — trace IDs in MDC for distributed debugging
- **Automatic retry** — `@Retryable` with attempt count in the platform result
- **Playwright integration** — browser lifecycle, console/network listeners, auto-screenshot
- **Rule-based failure hints** — pre-classifies failures before Claude AI analysis

---

## 1. Add the dependency

```xml
<dependency>
    <groupId>com.platform</groupId>
    <artifactId>platform-testframework</artifactId>
    <version>${project.version}</version>
    <scope>test</scope>
</dependency>
```

> No need to also add `platform-sdk` — it is a transitive dependency.

Configure `src/test/resources/platform.properties` exactly as described in
[platform-sdk/USAGE.md](../platform-sdk/USAGE.md#2-configure-the-sdk).

---

## 2. JUnit 5 — extend `PlatformBaseTest`

```java
@TestMetadata(owner = "payments-team", feature = "Checkout", severity = CRITICAL)
class CheckoutTest extends PlatformBaseTest {

    @Test
    void userCanCompleteCheckout() {
        log.step("Add item to cart");
          cart.addItem(ITEM_ID);
          log.info("Added item={}", ITEM_ID);
        log.endStep();

        log.step("Complete checkout");
          checkout.fillAddress(address);
          checkout.pay(card);
        log.endStep();

        log.step("Verify order confirmation");
          assertThat(confirmation.getOrderId()).isNotNull();
        log.endStep();
    }
}
```

`PlatformBaseTest` wires `PlatformExtension` (context, MDC, tracing, publishing) and
`RetryExtension` (`@Retryable` support) automatically via `@ExtendWith`.

### Protected members

| Member | Type | Description |
|---|---|---|
| `log` | `TestLogger` | Structured logger — writes to SLF4J + test context |
| `context()` | `TestContext` | Active test context (non-null inside `@Test` methods) |
| `env(key, value)` | `void` | Attach custom environment metadata to the result |
| `softly(consumer)` | `void` | Run a soft assertion block (all failures reported together) |

---

## 3. TestNG — extend `PlatformTestNGBase`

```java
public class LoginTest extends PlatformTestNGBase {

    @Test(groups = "smoke")
    public void userCanLogin() {
        log.step("Navigate to login page");
          driver.get(baseUrl + "/login");
        log.endStep();

        log.step("Submit credentials");
          loginPage.login("user@example.com", "password");
        log.endStep();

        log.step("Verify dashboard");
          softly(soft -> soft.assertThat(driver.getTitle()).contains("Dashboard"));
        log.endStep();
    }
}
```

`PlatformTestNGBase` pre-wires `@Listeners(PlatformTestNGListener.class)`. The same `log`,
`context()`, `env()`, and `softly()` members are available.

---

## 4. Cucumber — register `PlatformCucumberPlugin`

Add the plugin to `@CucumberOptions` or `cucumber.properties`. No step code changes needed —
the plugin automatically maps every Gherkin step to a platform step.

```java
@CucumberOptions(plugin = {
    "com.platform.testframework.cucumber.PlatformCucumberPlugin"
})
public class SauceRunner {}
```

```properties
# src/test/resources/cucumber.properties
cucumber.plugin=com.platform.testframework.cucumber.PlatformCucumberPlugin
```

For richer logging inside step definitions, use `TestLogger` directly:

```java
public class LoginSteps {
    private final TestLogger log = TestLogger.forClass(LoginSteps.class);

    @When("the user submits the login form with {string}")
    public void submitForm(String username) {
        log.info("Submitting form username={}", username);
        loginPage.submit();
    }
}
```

---

## 5. Playwright integration

### Option A — `PlatformPlaywrightExtension` (managed lifecycle)

The extension creates and tears down `Playwright → Browser → BrowserContext → Page` per test,
auto-captures a screenshot + page source on failure, and injects the `Page` as a test parameter.

```java
class LoginTest extends PlatformBaseTest {

    @RegisterExtension
    PlatformPlaywrightExtension pw = PlatformPlaywrightExtension.chromium()
            .headless(true)
            .viewport(1920, 1080);

    @Test
    void userCanLogin(Page page) {
        log.step("Navigate to login");
          page.navigate(APP_URL + "/login");
        log.endStep();

        log.step("Submit credentials");
          page.locator("#username").fill("admin");
          page.locator("#password").fill("secret");
          page.locator("[type=submit]").click();
        log.endStep();

        assertThat(page.locator(".dashboard")).isVisible();
    }
}
```

**Factory methods:**

```java
PlatformPlaywrightExtension.chromium()   // default
PlatformPlaywrightExtension.firefox()
PlatformPlaywrightExtension.webkit()
```

**Builder options:**

```java
.headless(boolean)         // default: true
.slowMo(double ms)         // slow down actions (useful for debugging), default: 0
.viewport(int w, int h)    // default: 1920×1080
```

**Injected parameter types** (JUnit 5 `ParameterResolver`):

```java
void myTest(Page page)                    // the active page
void myTest(BrowserContext context)       // the active browser context
void myTest(Browser browser)              // the active browser
```

**Accessors** (for use in `@BeforeEach` / non-parameter scenarios):

```java
pw.getPage()       // current Page
pw.getContext()    // current BrowserContext
pw.getBrowser()    // current Browser
```

### Option B — `PlatformPlaywrightSupport` (manual lifecycle)

Use when you manage the `Playwright` instance yourself (e.g., sharing a browser across tests):

```java
Page page = browser.newPage();
PlatformPlaywrightSupport.attach(page);          // wire console/network/error listeners

// On test failure:
PlatformPlaywrightSupport.screenshotOnFailure(page, testName);
PlatformPlaywrightSupport.capturePageSource(page, testName);

// Anytime — explicit screenshot:
PlatformPlaywrightSupport.screenshot(page, "after-login");
```

`attach()` wires four listeners:
- Browser console errors/warnings → test log
- Uncaught JS exceptions → test log
- Failed network requests → test log
- Page navigations → test log (debug level)

---

## 6. `TestLogger` API

`TestLogger` is the primary logging interface. Every call writes to SLF4J (with MDC context for
Logstash/OpenSearch) **and** to the active `TestContext` so the output appears in the platform result.

```java
protected final TestLogger log = TestLogger.forClass(getClass());
```

### Step lifecycle

```java
// Open a step (pass description, close manually)
log.step("Navigate to login page");
  page.navigate(url);
log.endStep();

// Open a step with lambda (auto-closed on success or failure)
log.step("Submit credentials", () -> {
    loginPage.fill(username, password);
    loginPage.submit();
});

// Steps can be nested
log.step("Complete purchase");
  log.step("Fill shipping address");
    shippingPage.fill(address);
  log.endStep();
  log.step("Enter payment");
    paymentPage.fill(card);
  log.endStep();
log.endStep();
```

### Log levels

```java
log.info("Status code: {}", response.statusCode());
log.warn("Retry attempt {}", attempt);
log.error("Unexpected exception: {}", e.getMessage());
log.debug("Raw response: {}", body);   // debug is NOT sent to the platform (too verbose)
```

### Attachments

```java
log.attach("screenshot.png", screenshotBytes, "image/png");
log.attach("response.json",  responseBytes,   "application/json");
log.attach("page.html",      htmlBytes,        "text/html");
```

Attachments are written to a temp file and the path is recorded in the test context.

---

## 7. Annotations

### `@TestMetadata`

Attaches metadata to a test class or method. Values flow to the platform as tags.

```java
@TestMetadata(
    owner    = "payments-team",
    feature  = "Checkout",
    story    = "PLAT-456",
    severity = TestMetadata.Severity.CRITICAL
)
class CheckoutTest extends PlatformBaseTest { ... }
```

| Attribute | Type | Default | Values |
|---|---|---|---|
| `owner` | `String` | `""` | Team or person name |
| `feature` | `String` | `""` | Business feature |
| `story` | `String` | `""` | JIRA/Linear ticket key |
| `severity` | `Severity` | `NORMAL` | `BLOCKER`, `CRITICAL`, `NORMAL`, `MINOR`, `TRIVIAL` |

### `@Retryable`

Retries a flaky test on failure. Each failed attempt is published individually with the attempt number.

```java
@Test
@Retryable(maxAttempts = 3)
void flakyIntegrationTest() {
    // will run up to 3 times; passes as soon as one attempt succeeds
}
```

| Attribute | Type | Default | Description |
|---|---|---|---|
| `maxAttempts` | `int` | `3` | Total attempts including the first run |

---

## 8. Custom environment metadata

Use `env()` to attach dynamic context — browser version, device, base URL, etc. — to the test result:

```java
class BrowserTest extends PlatformBaseTest {

    @BeforeEach
    void setUp() {
        env("browser",         driver.getCapabilities().getBrowserName());
        env("browser.version", driver.getCapabilities().getBrowserVersion());
        env("app.url",         System.getenv("APP_URL"));
        env("device",          "Desktop 1920x1080");
    }
}
```

---

## 9. Soft assertions

Collect multiple assertion failures in one test instead of stopping at the first:

```java
@Test
void dashboardDisplaysCorrectly() {
    softly(soft -> {
        soft.assertThat(page.getTitle()).isEqualTo("Dashboard");
        soft.assertThat(page.getGreeting()).contains("Welcome");
        soft.assertThat(page.getNotificationCount()).isGreaterThan(0);
    });
    // All three assertions run; failures are reported together
}
```

---

## 10. Failure classification

`FailureClassifier` runs automatically before publishing. It analyzes the thrown exception,
current step name, and captured log to produce a `FailureHint` with a category and confidence score.
These are attached to the result as environment metadata and used by the platform's AI service.

**Categories detected:**

| Category | Examples |
|---|---|
| `BAD_LOCATOR` | `NoSuchElementException`, Playwright locator timeout, `unable to locate element` |
| `FLAKY_TIMING` | `StaleElementReferenceException`, click intercepted, element not interactable |
| `TIMEOUT` | Generic network/socket timeouts, Selenium `TimeoutException` |
| `INFRASTRUCTURE` | `ConnectException`, `UnknownHostException`, browser crash, SSL failure |
| `APPLICATION_BUG` | Assertion failures on HTTP status, verify/assert step failures, NPE in app code |
| `TEST_CODE_BUG` | NPE / `ClassCastException` in test/page-object/steps code |
| `UNKNOWN` | Anything not matched above (sent to Claude AI for deeper analysis) |

The hint fields are available in the platform result as:
```
platform.hint.category    = BAD_LOCATOR
platform.hint.confidence  = 0.95
platform.hint.message     = Element not found. The selector '.checkout-btn' did not match...
```

---

## 11. CI environment detection

`EnvironmentInfo.collect()` is called automatically and attached to every result. It captures:

| Key | Source |
|---|---|
| `java.version`, `os.name`, `os.arch` | JVM system properties |
| `ci.provider` | Detected from env vars: `github-actions`, `gitlab-ci`, `jenkins`, `circleci`, `azure-devops`, `bitbucket`, `teamcity`, `travis-ci`, `local` |
| `ci.build.id`, `ci.build.url` | CI-provider-specific env vars |
| `ci.commit`, `ci.branch` | `GITHUB_SHA` / `CI_COMMIT_SHA` / `GIT_COMMIT` etc. |
| `execution.mode` | `PARALLEL` or `SEQUENTIAL` (inferred from JUnit/Cucumber config) |
| `execution.parallelism` | Thread count |
| `test.env` | `$TEST_ENV` |
| `app.url` | `$APP_URL` |

---

## 12. Module dependency map

```
platform-testframework
  ├── platform-sdk          (HTTP publisher, PlatformConfig, PlatformReporter)
  ├── platform-common       (UnifiedTestResult, TestCaseResultDto, enums)
  ├── junit-jupiter-api     (compile — default runner)
  ├── testng                (optional — TestNG adapter)
  ├── cucumber-java/plugin  (optional — Cucumber adapter)
  ├── playwright            (optional — Playwright integration)
  ├── assertj-core          (soft assertions)
  ├── logback-classic       (SLF4J implementation)
  ├── opentelemetry-api     (trace/span IDs in MDC)
  └── logstash-logback-encoder (structured JSON log shipping)
```

Add only the runner you actually use. Playwright, TestNG, and Cucumber are declared `optional`
in the pom — they compile if the library is on the classpath but are never forced on teams that
don't need them.
