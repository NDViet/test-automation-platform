package com.platform.tests.saucedemo.steps;

import com.platform.tests.saucedemo.context.ScenarioContext;
import com.platform.tests.saucedemo.pages.InventoryPage;
import com.platform.tests.saucedemo.pages.LoginPage;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for inventory.feature (and reused in cart.feature Background).
 */
public class InventorySteps {

    private final ScenarioContext ctx;
    private final LoginPage loginPage;
    private final InventoryPage inventoryPage;

    public InventorySteps(ScenarioContext ctx) {
        this.ctx = ctx;
        this.loginPage = new LoginPage(ctx.page());
        this.inventoryPage = new InventoryPage(ctx.page());
    }

    @Given("I am logged in as {string}")
    public void iAmLoggedInAs(String username) {
        loginPage.navigate(ctx.baseUrl());
        loginPage.login(username, "secret_sauce");
        assertThat(loginPage.isOnInventoryPage())
                .as("Login should succeed for user: " + username)
                .isTrue();
    }

    @Given("I am on the inventory page")
    public void iAmOnTheInventoryPage() {
        // Already there after login; navigate if not
        if (!ctx.page().url().contains("/inventory.html")) {
            inventoryPage.navigate(ctx.baseUrl());
        }
    }

    @Then("I should see {int} products listed")
    public void iShouldSeeProductsListed(int expectedCount) {
        assertThat(inventoryPage.getProductCount())
                .as("Number of products on the inventory page")
                .isEqualTo(expectedCount);
    }

    @Then("each product should have a name, price, and add-to-cart button")
    public void eachProductShouldHaveNamePriceAndButton() {
        assertThat(inventoryPage.allProductsHaveNamePriceAndButton())
                .as("Each product should have a name, price, and add-to-cart button")
                .isTrue();
    }

    @When("I sort products by {string}")
    public void iSortProductsBy(String sortOption) {
        inventoryPage.sortBy(sortOption);
    }

    @Then("the products should be sorted alphabetically ascending")
    public void theProductsShouldBeSortedAlphabeticallyAscending() {
        List<String> names = inventoryPage.getProductNames();
        List<String> sorted = names.stream().sorted().toList();
        assertThat(names)
                .as("Product names should be sorted A→Z")
                .isEqualTo(sorted);
    }

    @Then("the products should be sorted by price ascending")
    public void theProductsShouldBeSortedByPriceAscending() {
        List<Double> prices = inventoryPage.getProductPrices();
        List<Double> sorted = prices.stream().sorted().toList();
        assertThat(prices)
                .as("Product prices should be sorted low→high")
                .isEqualTo(sorted);
    }

    @When("I add {string} to the cart")
    public void iAddToTheCart(String productName) {
        inventoryPage.addToCart(productName);
    }

    @Then("the cart badge should show {string}")
    public void theCartBadgeShouldShow(String expectedCount) {
        assertThat(inventoryPage.getCartBadgeText())
                .as("Cart badge count")
                .isEqualTo(expectedCount);
    }

    @Then("the {string} button should change to {string}")
    public void theButtonShouldChangeTo(String productName, String expectedLabel) {
        if ("Remove".equals(expectedLabel)) {
            assertThat(inventoryPage.isButtonRemove(productName))
                    .as("Button for '" + productName + "' should show 'Remove'")
                    .isTrue();
        }
    }

    @And("I navigate to the cart")
    public void iNavigateToTheCart() {
        inventoryPage.goToCart();
        ctx.page().waitForSelector(".cart_contents_container");
    }
}
