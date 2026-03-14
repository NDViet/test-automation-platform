package com.example.saucedemo.context;

import io.cucumber.java.Scenario;
import org.openqa.selenium.WebDriver;

/**
 * Mutable holder shared across step classes and hooks via PicoContainer DI.
 *
 * <p>PicoContainer creates one instance per scenario and injects it into
 * every constructor that declares it as a parameter, so there is no need for
 * static fields or explicit ThreadLocals.</p>
 */
public class ScenarioContext {

    private WebDriver driver;
    private Scenario  scenario;

    public WebDriver getDriver() {
        return driver;
    }

    public void setDriver(WebDriver driver) {
        this.driver = driver;
    }

    public Scenario getScenario() {
        return scenario;
    }

    public void setScenario(Scenario scenario) {
        this.scenario = scenario;
    }
}
