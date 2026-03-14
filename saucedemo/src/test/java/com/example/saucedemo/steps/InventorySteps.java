package com.example.saucedemo.steps;

import com.example.saucedemo.context.ScenarioContext;
import com.example.saucedemo.pages.InventoryPage;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

public class InventorySteps {

    private final ScenarioContext ctx;
    private InventoryPage inventoryPage;

    public InventorySteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    private InventoryPage page() {
        if (inventoryPage == null) inventoryPage = new InventoryPage(ctx.getDriver());
        return inventoryPage;
    }

    @Then("I should see {int} products on the inventory page")
    public void iShouldSeeNProducts(int expected) {
        assertThat(page().productCount())
                .as("Number of products on inventory page")
                .isEqualTo(expected);
    }

    @When("I sort products by {string}")
    public void iSortProductsBy(String sortOption) {
        page().sortBy(sortOption);
    }

    @Then("the first product name should be {string}")
    public void theFirstProductNameShouldBe(String expectedName) {
        assertThat(page().firstProductName())
                .as("First product name after sorting")
                .isEqualTo(expectedName);
    }

    @When("I add {string} to the cart from the inventory")
    public void iAddProductToCartFromInventory(String productName) {
        page().addToCart(productName);
    }

    @And("I remove {string} from the cart from the inventory")
    public void iRemoveProductFromCartFromInventory(String productName) {
        page().removeFromCart(productName);
    }

    @Then("the cart badge should show {string}")
    public void theCartBadgeShouldShow(String expectedCount) {
        assertThat(page().cartBadgeText())
                .as("Cart badge count")
                .isEqualTo(expectedCount);
    }

    @Then("the cart badge should not be visible")
    public void theCartBadgeShouldNotBeVisible() {
        assertThat(page().isCartBadgeVisible())
                .as("Cart badge should not be visible after removing all items")
                .isFalse();
    }

    /**
     * Used in the checkout Background to pre-load the cart before navigating to it.
     * Assumes the caller is already on the inventory page (logged-in state from Background).
     */
    @And("{string} is in my cart")
    public void productIsInMyCart(String productName) {
        page().addToCart(productName);
    }
}
