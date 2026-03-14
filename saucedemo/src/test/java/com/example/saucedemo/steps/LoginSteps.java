package com.example.saucedemo.steps;

import com.example.saucedemo.context.ScenarioContext;
import com.example.saucedemo.pages.InventoryPage;
import com.example.saucedemo.pages.LoginPage;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

public class LoginSteps {

    private final ScenarioContext ctx;
    private LoginPage    loginPage;
    private InventoryPage inventoryPage;

    public LoginSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Given("I am on the login page")
    public void iAmOnTheLoginPage() {
        loginPage = new LoginPage(ctx.getDriver());
        loginPage.open();
    }

    @When("I log in as {string} with password {string}")
    public void iLogInAs(String username, String password) {
        if (loginPage == null) loginPage = new LoginPage(ctx.getDriver());
        loginPage.loginWith(username, password);
    }

    @When("I submit the login form without entering credentials")
    public void iSubmitLoginWithoutCredentials() {
        if (loginPage == null) loginPage = new LoginPage(ctx.getDriver());
        loginPage.submitWithoutCredentials();
    }

    @Then("I should see the products page")
    public void iShouldSeeTheProductsPage() {
        inventoryPage = new InventoryPage(ctx.getDriver());
        assertThat(inventoryPage.isLoaded())
                .as("Expected to be on /inventory.html but URL was: " + ctx.getDriver().getCurrentUrl())
                .isTrue();
    }

    @Then("the page title should be {string}")
    public void thePageTitleShouldBe(String expectedTitle) {
        if (inventoryPage == null) inventoryPage = new InventoryPage(ctx.getDriver());
        assertThat(inventoryPage.pageTitle())
                .as("Inventory page title")
                .isEqualTo(expectedTitle);
    }

    @Then("I should see the error message {string}")
    public void iShouldSeeTheErrorMessage(String expectedMessage) {
        if (loginPage == null) loginPage = new LoginPage(ctx.getDriver());
        assertThat(loginPage.errorMessage())
                .as("Login error message")
                .contains(expectedMessage);
    }

    @When("I log out")
    public void iLogOut() {
        if (loginPage == null) loginPage = new LoginPage(ctx.getDriver());
        loginPage.logout();
    }

    @Then("I should be on the login page")
    public void iShouldBeOnTheLoginPage() {
        if (loginPage == null) loginPage = new LoginPage(ctx.getDriver());
        assertThat(loginPage.isOnLoginPage())
                .as("Expected to be on the login page")
                .isTrue();
    }

    /**
     * Shared step reused by the Background in inventory, cart, and checkout features.
     * Navigates to the login page, authenticates, and verifies the inventory is visible.
     */
    @Given("I am logged in as {string}")
    public void iAmLoggedInAs(String username) {
        loginPage = new LoginPage(ctx.getDriver());
        loginPage.open();
        loginPage.loginWith(username, "secret_sauce");
        inventoryPage = new InventoryPage(ctx.getDriver());
        assertThat(inventoryPage.isLoaded())
                .as("Login with '" + username + "' should succeed and land on inventory")
                .isTrue();
    }
}
