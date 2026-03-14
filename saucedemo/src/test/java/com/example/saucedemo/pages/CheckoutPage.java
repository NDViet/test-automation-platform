package com.example.saucedemo.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.List;

/**
 * Page object covering all three checkout steps:
 * <ol>
 *   <li>{@code /checkout-step-one.html}  — personal info form</li>
 *   <li>{@code /checkout-step-two.html}  — order overview</li>
 *   <li>{@code /checkout-complete.html}  — confirmation</li>
 * </ol>
 */
public class CheckoutPage extends BasePage {

    // Step 1
    private static final By FIRST_NAME   = By.id("first-name");
    private static final By LAST_NAME    = By.id("last-name");
    private static final By POSTAL_CODE  = By.id("postal-code");
    private static final By CONTINUE_BTN = By.id("continue");
    private static final By STEP1_ERROR  = By.cssSelector("[data-test='error']");

    // Step 2
    private static final By SUMMARY_ITEM_NAME = By.cssSelector(".inventory_item_name");
    private static final By FINISH_BTN        = By.id("finish");

    // Step 3
    private static final By COMPLETE_HEADER = By.cssSelector(".complete-header");

    public CheckoutPage(WebDriver driver) {
        super(driver);
    }

    // ── Step 1 ─────────────────────────────────────────────────────────────────

    public void enterInfo(String firstName, String lastName, String postalCode) {
        log.info("Entering checkout info: '{}' '{}' '{}'", firstName, lastName, postalCode);
        // Wait for navigation from cart to checkout step 1 before touching the form
        wait.until(ExpectedConditions.urlContains("/checkout-step-one.html"));
        waitForVisible(FIRST_NAME).clear();
        driver.findElement(FIRST_NAME).sendKeys(firstName);
        driver.findElement(LAST_NAME).sendKeys(lastName);
        driver.findElement(POSTAL_CODE).sendKeys(postalCode);
    }

    public void continueToOverview() {
        waitForClickable(CONTINUE_BTN).click();
    }

    public String step1ErrorMessage() {
        return waitForVisible(STEP1_ERROR).getText();
    }

    // ── Step 2 ─────────────────────────────────────────────────────────────────

    public boolean isOnOverviewPage() {
        return driver.getCurrentUrl().contains("/checkout-step-two.html");
    }

    public List<String> summaryItemNames() {
        return driver.findElements(SUMMARY_ITEM_NAME)
                .stream()
                .map(e -> e.getText())
                .toList();
    }

    public void finishOrder() {
        log.info("Clicking 'Finish'");
        waitForClickable(FINISH_BTN).click();
    }

    // ── Step 3 ─────────────────────────────────────────────────────────────────

    public String confirmationHeader() {
        return waitForVisible(COMPLETE_HEADER).getText();
    }
}
