package com.example.saucedemo.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Base class for all SauceDemo page objects.
 *
 * <p>Provides a shared {@link WebDriverWait} (10-second timeout) and convenience
 * helpers for waiting on elements. All interactions go through the wait so tests
 * are resilient to minor page-load delays without using {@code Thread.sleep}.</p>
 */
public abstract class BasePage {

    protected static final String BASE_URL = "https://www.saucedemo.com";
    private static final Duration DEFAULT_WAIT = Duration.ofSeconds(10);

    protected final WebDriver     driver;
    protected final WebDriverWait wait;
    protected final Logger        log;

    protected BasePage(WebDriver driver) {
        this.driver = driver;
        this.wait   = new WebDriverWait(driver, DEFAULT_WAIT);
        this.log    = LoggerFactory.getLogger(getClass());
    }

    protected WebElement waitForVisible(By locator) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    protected WebElement waitForClickable(By locator) {
        return wait.until(ExpectedConditions.elementToBeClickable(locator));
    }

    protected boolean isElementPresent(By locator) {
        try {
            driver.findElement(locator);
            return true;
        } catch (org.openqa.selenium.NoSuchElementException e) {
            return false;
        }
    }
}
