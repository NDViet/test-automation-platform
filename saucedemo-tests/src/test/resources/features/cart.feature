@cart @checkout
Feature: SauceDemo Cart and Checkout
  As a logged-in shopper
  I want to manage my cart and complete a purchase
  So that my order is placed successfully

  Background:
    Given I am logged in as "standard_user"
    And I am on the inventory page

  @smoke
  Scenario: View cart after adding products
    When I add "Sauce Labs Backpack" to the cart
    And I navigate to the cart
    Then the cart should contain "Sauce Labs Backpack"
    And the cart item count should be 1

  Scenario: Remove a product from the cart
    When I add "Sauce Labs Backpack" to the cart
    And I navigate to the cart
    And I remove "Sauce Labs Backpack" from the cart
    Then the cart should be empty
    And the cart badge should not be visible

  @smoke
  Scenario: Complete checkout with a single product
    When I add "Sauce Labs Backpack" to the cart
    And I navigate to the cart
    And I proceed to checkout
    And I enter shipping details with first name "John", last name "Doe", and zip "12345"
    And I complete the order
    Then I should see the order confirmation message "Thank you for your order!"

  Scenario: Checkout total matches product price
    When I add "Sauce Labs Backpack" to the cart
    And I navigate to the cart
    And I proceed to checkout
    And I enter shipping details with first name "Jane", last name "Smith", and zip "90210"
    Then the item total should match the product price
