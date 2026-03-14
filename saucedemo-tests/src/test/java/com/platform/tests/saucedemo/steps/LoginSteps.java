package com.platform.tests.saucedemo.steps;

import com.platform.tests.saucedemo.context.ScenarioContext;
import com.platform.tests.saucedemo.pages.LoginPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for login.feature.
 *
 * <p>Uses {@link LoginPage} for browser interactions.
 * {@link ScenarioContext} is injected by PicoContainer — the same
 * instance shared with all step classes in this scenario.</p>
 */
public class LoginSteps {

    private final ScenarioContext ctx;
    private final LoginPage loginPage;

    public LoginSteps(ScenarioContext ctx) {
        this.ctx = ctx;
        this.loginPage = new LoginPage(ctx.page());
    }

    @Given("I am on the SauceDemo login page")
    public void iAmOnTheSauceDemoLoginPage() {
        loginPage.navigate(ctx.baseUrl());
    }

    @When("I log in with username {string} and password {string}")
    public void iLogInWith(String username, String password) {
        loginPage.login(username, password);
    }

    @Then("I should see the products page")
    public void iShouldSeeTheProductsPage() {
        assertThat(loginPage.isOnInventoryPage())
                .as("Expected to be on the inventory page after login")
                .isTrue();
    }

    @Then("the page title should be {string}")
    public void thePageTitleShouldBe(String expectedTitle) {
        assertThat(ctx.page().title())
                .as("Page title")
                .isEqualTo(expectedTitle);
    }

    @Then("I should see the error message {string}")
    public void iShouldSeeTheErrorMessage(String expectedMessage) {
        assertThat(loginPage.hasError())
                .as("Error message should be visible")
                .isTrue();
        assertThat(loginPage.getErrorMessage())
                .as("Error message text")
                .contains(expectedMessage);
    }
}
