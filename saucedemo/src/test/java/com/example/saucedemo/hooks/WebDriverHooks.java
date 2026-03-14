package com.example.saucedemo.hooks;

import com.example.saucedemo.context.ScenarioContext;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cucumber hooks that manage the ChromeDriver lifecycle for each scenario.
 *
 * <p><b>Before</b> — initialises a fresh ChromeDriver (headless by default;
 * pass {@code -Dheadless=false} to watch the browser).</p>
 *
 * <p><b>After</b> — captures a screenshot and attaches it to the Cucumber
 * report when the scenario fails, then quits the driver.</p>
 */
public class WebDriverHooks {

    private static final Logger log = LoggerFactory.getLogger(WebDriverHooks.class);

    private final ScenarioContext ctx;

    /** PicoContainer injects the shared ScenarioContext. */
    public WebDriverHooks(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Before
    public void setUpDriver(Scenario scenario) {
        ctx.setScenario(scenario);

        boolean headless = Boolean.parseBoolean(System.getProperty("headless", "true"));
        log.info("Starting ChromeDriver — scenario: '{}' headless={}", scenario.getName(), headless);

        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        if (headless) {
            options.addArguments("--headless=new");
        }
        options.addArguments(
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--window-size=1280,800"
        );

        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(
                java.time.Duration.ofSeconds(0) // explicit waits only — see BasePage
        );
        ctx.setDriver(driver);
    }

    @After
    public void tearDownDriver(Scenario scenario) {
        WebDriver driver = ctx.getDriver();
        if (driver == null) return;

        try {
            if (scenario.isFailed()) {
                log.warn("Scenario '{}' FAILED — capturing screenshot", scenario.getName());
                byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
                scenario.attach(screenshot, "image/png", "screenshot-on-failure");
            }
        } catch (Exception e) {
            log.warn("Could not capture screenshot: {}", e.getMessage());
        } finally {
            driver.quit();
            ctx.setDriver(null);
            log.info("ChromeDriver closed — scenario: '{}'", scenario.getName());
        }
    }
}
