package com.example.saucedemo.steps;

import com.example.saucedemo.context.ScenarioContext;
import com.example.saucedemo.pages.CheckoutPage;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

public class CheckoutSteps {

    private final ScenarioContext ctx;
    private CheckoutPage checkoutPage;

    public CheckoutSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    private CheckoutPage page() {
        if (checkoutPage == null) checkoutPage = new CheckoutPage(ctx.getDriver());
        return checkoutPage;
    }

    @And("I enter checkout information: first name {string}, last name {string}, postal code {string}")
    public void iEnterCheckoutInformation(String firstName, String lastName, String postalCode) {
        page().enterInfo(firstName, lastName, postalCode);
    }

    @And("I continue to the order overview")
    public void iContinueToTheOrderOverview() {
        page().continueToOverview();
    }

    @Then("I should see the order summary with {string}")
    public void iShouldSeeTheOrderSummaryWith(String productName) {
        assertThat(page().isOnOverviewPage())
                .as("Should be on checkout step 2 (overview)")
                .isTrue();
        assertThat(page().summaryItemNames())
                .as("Order overview should contain '" + productName + "'")
                .contains(productName);
    }

    @When("I finish the order")
    public void iFinishTheOrder() {
        page().finishOrder();
    }

    @Then("I should see the order confirmation message {string}")
    public void iShouldSeeTheOrderConfirmationMessage(String expectedMessage) {
        assertThat(page().confirmationHeader())
                .as("Order confirmation header")
                .isEqualTo(expectedMessage);
    }

    @Then("I should see the checkout error {string}")
    public void iShouldSeeTheCheckoutError(String expectedError) {
        assertThat(page().step1ErrorMessage())
                .as("Checkout step 1 error")
                .contains(expectedError);
    }
}
