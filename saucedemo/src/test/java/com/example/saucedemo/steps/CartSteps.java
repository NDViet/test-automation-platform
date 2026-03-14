package com.example.saucedemo.steps;

import com.example.saucedemo.context.ScenarioContext;
import com.example.saucedemo.pages.CartPage;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

public class CartSteps {

    private final ScenarioContext ctx;
    private CartPage cartPage;

    public CartSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    private CartPage page() {
        if (cartPage == null) cartPage = new CartPage(ctx.getDriver());
        return cartPage;
    }

    @When("I navigate to the cart page")
    public void iNavigateToTheCartPage() {
        ctx.getDriver().get("https://www.saucedemo.com/cart.html");
        assertThat(page().isLoaded())
                .as("Should be on /cart.html")
                .isTrue();
    }

    @Then("the cart should contain {string}")
    public void theCartShouldContain(String productName) {
        assertThat(page().containsItem(productName))
                .as("Cart should contain '" + productName + "'; actual items: " + page().itemNames())
                .isTrue();
    }

    @Then("the cart should contain {int} items")
    public void theCartShouldContainNItems(int expectedCount) {
        assertThat(page().itemCount())
                .as("Cart item count")
                .isEqualTo(expectedCount);
    }

    @And("I remove {string} from the cart page")
    public void iRemoveItemFromCartPage(String productName) {
        page().removeItem(productName);
    }

    @Then("the cart should be empty")
    public void theCartShouldBeEmpty() {
        assertThat(page().isEmpty())
                .as("Cart should be empty")
                .isTrue();
    }

    @And("I click continue shopping")
    public void iClickContinueShopping() {
        page().continueShopping();
    }

    @And("I proceed to checkout")
    public void iProceedToCheckout() {
        page().proceedToCheckout();
    }
}
