@login
Feature: Login
  As a shopper
  I want to log in to SauceDemo
  So that I can browse and purchase products

  Scenario: Standard user logs in successfully
    Given I am on the login page
    When I log in as "standard_user" with password "secret_sauce"
    Then I should see the products page
    And the page title should be "Products"

  Scenario: Locked-out user is blocked
    Given I am on the login page
    When I log in as "locked_out_user" with password "secret_sauce"
    Then I should see the error message "Sorry, this user has been locked out."

  Scenario: Invalid credentials show error
    Given I am on the login page
    When I log in as "invalid_user" with password "wrong_pass"
    Then I should see the error message "Username and password do not match any user in this service"

  Scenario: Empty credentials are rejected
    Given I am on the login page
    When I submit the login form without entering credentials
    Then I should see the error message "Username is required"

  @smoke
  Scenario: Logout returns to login page
    Given I am on the login page
    When I log in as "standard_user" with password "secret_sauce"
    Then I should see the products page
    When I log out
    Then I should be on the login page
