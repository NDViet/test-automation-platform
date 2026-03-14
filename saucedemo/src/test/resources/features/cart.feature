@cart
Feature: Shopping Cart
  As a logged-in shopper
  I want to manage items in my cart
  So that I can review before checkout

  Background:
    Given I am logged in as "standard_user"

  @smoke
  Scenario: Added product appears in the cart
    When I add "Sauce Labs Backpack" to the cart from the inventory
    And I navigate to the cart page
    Then the cart should contain "Sauce Labs Backpack"

  Scenario: Multiple products appear in the cart
    When I add "Sauce Labs Backpack" to the cart from the inventory
    And I add "Sauce Labs Bike Light" to the cart from the inventory
    And I navigate to the cart page
    Then the cart should contain 2 items

  Scenario: Removing an item from the cart page empties the cart
    When I add "Sauce Labs Backpack" to the cart from the inventory
    And I navigate to the cart page
    And I remove "Sauce Labs Backpack" from the cart page
    Then the cart should be empty

  Scenario: Continue shopping returns to the inventory
    When I navigate to the cart page
    And I click continue shopping
    Then I should see the products page
