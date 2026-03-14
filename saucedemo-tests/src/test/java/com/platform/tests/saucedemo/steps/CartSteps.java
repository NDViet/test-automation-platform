package com.platform.tests.saucedemo.steps;

import com.platform.tests.saucedemo.context.ScenarioContext;
import com.platform.tests.saucedemo.pages.CartPage;
import com.platform.tests.saucedemo.pages.CheckoutPage;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Step definitions for cart.feature.
 */
public class CartSteps {

    private final ScenarioContext ctx;
    private final CartPage cartPage;
    private final CheckoutPage checkoutPage;

    public CartSteps(ScenarioContext ctx) {
        this.ctx = ctx;
        this.cartPage = new CartPage(ctx.page());
        this.checkoutPage = new CheckoutPage(ctx.page());
    }

    @Then("the cart should contain {string}")
    public void theCartShouldContain(String productName) {
        assertThat(cartPage.containsItem(productName))
                .as("Cart should contain: " + productName)
                .isTrue();
    }

    @Then("the cart item count should be {int}")
    public void theCartItemCountShouldBe(int expectedCount) {
        assertThat(cartPage.getCartItemCount())
                .as("Cart item count")
                .isEqualTo(expectedCount);
    }

    @When("I remove {string} from the cart")
    public void iRemoveFromTheCart(String productName) {
        cartPage.removeItem(productName);
    }

    @Then("the cart should be empty")
    public void theCartShouldBeEmpty() {
        assertThat(cartPage.isEmpty())
                .as("Cart should be empty after removing all items")
                .isTrue();
    }

    @Then("the cart badge should not be visible")
    public void theCartBadgeShouldNotBeVisible() {
        assertThat(cartPage.isCartBadgeVisible())
                .as("Cart badge should not be visible when cart is empty")
                .isFalse();
    }

    @And("I proceed to checkout")
    public void iProceedToCheckout() {
        cartPage.proceedToCheckout();
    }

    @And("I enter shipping details with first name {string}, last name {string}, and zip {string}")
    public void iEnterShippingDetails(String firstName, String lastName, String zip) {
        checkoutPage.fillShippingInfo(firstName, lastName, zip);
    }

    @And("I complete the order")
    public void iCompleteTheOrder() {
        checkoutPage.finishOrder();
    }

    @Then("I should see the order confirmation message {string}")
    public void iShouldSeeTheOrderConfirmationMessage(String expectedMessage) {
        assertThat(checkoutPage.getConfirmationMessage())
                .as("Order confirmation message")
                .isEqualTo(expectedMessage);
    }

    @Then("the item total should match the product price")
    public void theItemTotalShouldMatchTheProductPrice() {
        // Sauce Labs Backpack is $29.99
        double itemTotal = checkoutPage.getItemTotal();
        assertThat(itemTotal)
                .as("Item total on checkout overview")
                .isGreaterThan(0.0)
                .isCloseTo(29.99, offset(0.01));
    }
}
