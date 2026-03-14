package com.example.theinternet.tests;

import com.example.theinternet.base.BaseTest;
import com.example.theinternet.pages.LoginPage;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two intentional edge-case scenarios that demonstrate FAILED and SKIPPED
 * result statuses in the platform quality report.
 *
 * <h3>reLoginShowsPersonalisedGreeting — FAILS</h3>
 * <p>After a logout-then-login cycle the test asserts that the flash banner
 * shows a personalised greeting ("Welcome back, Tom Smith!"). The application
 * never returns that text — it always says "You logged into a secure area!" —
 * so the assertion fails and the test is recorded as {@code FAILED}.</p>
 *
 * <h3>setupVerifyAdminCredentials → adminCanDownloadReport — SKIP</h3>
 * <p>{@code setupVerifyAdminCredentials} simulates a mandatory environment
 * pre-flight check: it tries to log in with admin credentials ({@code admin} /
 * {@code secret}) that do not exist on the-internet.  That test <em>fails</em>,
 * and because {@code adminCanDownloadReport} declares
 * {@code dependsOnMethods = "setupVerifyAdminCredentials"}, TestNG
 * automatically marks the downstream test as {@code SKIPPED} without ever
 * running it.</p>
 */
public class EdgeCaseTest extends BaseTest {

    // -----------------------------------------------------------------------
    // SCENARIO 1 — intentional assertion failure
    // -----------------------------------------------------------------------

    @Test(description = "After logout and re-login the app should greet the user by name")
    public void reLoginShowsPersonalisedGreeting() {
        recordBrowserEnv();

        log.step("Login as tomsmith");
            LoginPage loginPage = new LoginPage(page, log).open();
            loginPage.loginWith("tomsmith", "SuperSecretPassword!");
            assertThat(loginPage.isOnSecurePage()).as("First login should succeed").isTrue();
        log.endStep();

        log.step("Logout");
            page.locator("a[href='/logout']").click();
        log.endStep();

        log.step("Re-login with the same credentials");
            loginPage.loginWith("tomsmith", "SuperSecretPassword!");
        log.endStep();

        log.step("Verify personalised greeting is shown on second login");
            // The application always shows the generic "You logged into a secure area!"
            // banner — it never shows a personalised message.
            // This assertion intentionally fails to demonstrate FAILED status.
            assertThat(loginPage.flashMessage())
                    .as("Second login should show a personalised greeting")
                    .contains("Welcome back, Tom Smith!");
        log.endStep();
    }

    // -----------------------------------------------------------------------
    // SCENARIO 2 — skip due to failed setup pre-flight
    // -----------------------------------------------------------------------

    /**
     * Pre-flight check that validates admin credentials work in this environment.
     * On the-internet, {@code admin/secret} are not valid — the assertion fails,
     * which causes {@link #adminCanDownloadReport} to be SKIPPED by TestNG.
     */
    @Test(description = "Pre-flight: verify admin credentials are configured in this environment")
    public void setupVerifyAdminCredentials() {
        recordBrowserEnv();

        log.step("Attempt login with admin credentials");
            LoginPage loginPage = new LoginPage(page, log).open();
            loginPage.loginWith("admin", "secret");
        log.endStep();

        log.step("Confirm admin access is granted");
            // admin/secret are NOT valid on the-internet — this fails,
            // triggering a SKIP on the downstream adminCanDownloadReport test.
            assertThat(loginPage.isOnSecurePage())
                    .as("Admin credentials must be valid before admin tests can run — "
                      + "set ADMIN_USER / ADMIN_PASS env vars to configure")
                    .isTrue();
        log.endStep();
    }

    @Test(
        dependsOnMethods = "setupVerifyAdminCredentials",
        description = "Admin can access the report download page — skipped when admin setup fails"
    )
    public void adminCanDownloadReport() {
        recordBrowserEnv();

        log.step("Navigate to the file download page");
            page.navigate(BASE_URL + "/download");
        log.endStep();

        log.step("Verify downloadable files are listed");
            assertThat(page.locator("div.example a").count())
                    .as("Download page should list at least one file")
                    .isGreaterThan(0);
        log.endStep();
    }
}
