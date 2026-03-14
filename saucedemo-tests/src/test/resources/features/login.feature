@login @smoke
Feature: SauceDemo Login
  As a shopper
  I want to log in to the SauceDemo store
  So that I can browse and purchase products

  Background:
    Given I am on the SauceDemo login page

  @happy-path
  Scenario: Standard user logs in successfully
    When I log in with username "standard_user" and password "secret_sauce"
    Then I should see the products page
    And the page title should be "Swag Labs"

  @negative
  Scenario: Locked out user cannot log in
    When I log in with username "locked_out_user" and password "secret_sauce"
    Then I should see the error message "Epic sadface: Sorry, this user has been locked out."

  @negative
  Scenario: Invalid credentials show an error
    When I log in with username "invalid_user" and password "wrong_password"
    Then I should see the error message "Epic sadface: Username and password do not match any user in this service"

  @negative
  Scenario: Empty username shows a validation error
    When I log in with username "" and password "secret_sauce"
    Then I should see the error message "Epic sadface: Username is required"
