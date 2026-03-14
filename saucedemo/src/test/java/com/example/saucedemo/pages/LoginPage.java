package com.example.saucedemo.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * Page object for {@code https://www.saucedemo.com/} (the login page).
 */
public class LoginPage extends BasePage {

    private static final By USERNAME_INPUT  = By.id("user-name");
    private static final By PASSWORD_INPUT  = By.id("password");
    private static final By LOGIN_BUTTON    = By.id("login-button");
    private static final By ERROR_MESSAGE   = By.cssSelector("[data-test='error']");
    private static final By BURGER_MENU     = By.id("react-burger-menu-btn");
    private static final By LOGOUT_LINK     = By.id("logout_sidebar_link");

    public LoginPage(WebDriver driver) {
        super(driver);
    }

    public LoginPage open() {
        log.info("Navigating to login page: {}", BASE_URL);
        driver.get(BASE_URL);
        waitForVisible(USERNAME_INPUT);
        return this;
    }

    public void loginWith(String username, String password) {
        log.info("Logging in as '{}'", username);
        waitForClickable(USERNAME_INPUT).clear();
        driver.findElement(USERNAME_INPUT).sendKeys(username);
        driver.findElement(PASSWORD_INPUT).sendKeys(password);
        driver.findElement(LOGIN_BUTTON).click();
    }

    public void submitWithoutCredentials() {
        log.info("Clicking login button without credentials");
        waitForClickable(LOGIN_BUTTON).click();
    }

    public String errorMessage() {
        return waitForVisible(ERROR_MESSAGE).getText();
    }

    public boolean isOnLoginPage() {
        return driver.getCurrentUrl().equals(BASE_URL + "/") || driver.getCurrentUrl().equals(BASE_URL);
    }

    public void logout() {
        log.info("Logging out");
        waitForClickable(BURGER_MENU).click();
        // Wait for sidebar to open, then JS-click the logout link to bypass
        // the slide-in animation which can block normal click() in headless mode.
        WebElement logoutLink = wait.until(ExpectedConditions.presenceOfElementLocated(LOGOUT_LINK));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", logoutLink);
        // Confirm navigation back to the login page
        wait.until(ExpectedConditions.visibilityOfElementLocated(USERNAME_INPUT));
    }
}
