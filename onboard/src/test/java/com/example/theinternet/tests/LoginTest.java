package com.example.theinternet.tests;

import com.example.theinternet.base.BaseTest;
import com.example.theinternet.pages.LoginPage;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the Form Authentication page ({@code /login}).
 *
 * <p>Demonstrates the platform step-logging API ({@code log.step} / {@code log.endStep})
 * which produces structured, timestamped steps in both the console output and the
 * platform test result visible in the reporting UI.</p>
 */
public class LoginTest extends BaseTest {

    @Test(groups = "smoke", description = "Valid credentials authenticate the user and redirect to /secure")
    public void validLoginRedirectsToSecurePage() {
        recordBrowserEnv();

        log.step("Open login page");
            LoginPage loginPage = new LoginPage(page, log).open();
        log.endStep();

        log.step("Submit valid credentials");
            loginPage.loginWith("tomsmith", "SuperSecretPassword!");
        log.endStep();

        log.step("Verify redirect to /secure and success banner");
            assertThat(loginPage.isOnSecurePage())
                    .as("Browser should be on /secure after successful login")
                    .isTrue();
            assertThat(loginPage.flashMessage())
                    .as("Flash banner should confirm login")
                    .contains("You logged into a secure area!");
            assertThat(loginPage.isLogoutVisible())
                    .as("Logout link should be visible after login")
                    .isTrue();
        log.endStep();
    }

    @Test(groups = "smoke", description = "Invalid credentials display an error and stay on /login")
    public void invalidLoginShowsErrorMessage() {
        recordBrowserEnv();

        log.step("Open login page");
            LoginPage loginPage = new LoginPage(page, log).open();
        log.endStep();

        log.step("Submit invalid credentials");
            loginPage.loginWith("wronguser", "wrongpass");
        log.endStep();

        log.step("Verify error message and no redirect");
            softly(soft -> {
                soft.assertThat(loginPage.flashMessage())
                        .as("Flash banner should report invalid username")
                        .contains("Your username is invalid!");
                soft.assertThat(loginPage.isOnSecurePage())
                        .as("Should still be on login page")
                        .isFalse();
            });
        log.endStep();
    }

    @Test(description = "Wrong password with valid username shows specific error")
    public void wrongPasswordShowsError() {
        recordBrowserEnv();

        log.step("Open login page");
            LoginPage loginPage = new LoginPage(page, log).open();
        log.endStep();

        log.step("Submit valid username with wrong password");
            loginPage.loginWith("tomsmith", "wrongpassword");
        log.endStep();

        log.step("Verify password error message");
            assertThat(loginPage.flashMessage())
                    .as("Flash banner should report invalid password")
                    .contains("Your password is invalid!");
            assertThat(loginPage.isOnSecurePage()).isFalse();
        log.endStep();
    }
}
