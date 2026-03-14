@checkout
Feature: Checkout
  As a logged-in shopper with items in my cart
  I want to complete the checkout flow
  So that I can place an order

  Background:
    Given I am logged in as "standard_user"
    And "Sauce Labs Backpack" is in my cart

  @smoke
  Scenario: Complete checkout places an order successfully
    When I navigate to the cart page
    And I proceed to checkout
    And I enter checkout information: first name "John", last name "Doe", postal code "12345"
    And I continue to the order overview
    Then I should see the order summary with "Sauce Labs Backpack"
    When I finish the order
    Then I should see the order confirmation message "Thank you for your order!"

  Scenario: Checkout requires first name
    When I navigate to the cart page
    And I proceed to checkout
    And I enter checkout information: first name "", last name "Doe", postal code "12345"
    And I continue to the order overview
    Then I should see the checkout error "First Name is required"

  Scenario: Checkout requires last name
    When I navigate to the cart page
    And I proceed to checkout
    And I enter checkout information: first name "John", last name "", postal code "12345"
    And I continue to the order overview
    Then I should see the checkout error "Last Name is required"

  Scenario: Checkout requires postal code
    When I navigate to the cart page
    And I proceed to checkout
    And I enter checkout information: first name "John", last name "Doe", postal code ""
    And I continue to the order overview
    Then I should see the checkout error "Postal Code is required"
