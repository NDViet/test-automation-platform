@inventory
Feature: Product Inventory
  As a logged-in shopper
  I want to browse and filter products
  So that I can find items to purchase

  Background:
    Given I am logged in as "standard_user"

  @smoke
  Scenario: Inventory page displays six products
    Then I should see 6 products on the inventory page

  Scenario: Products can be sorted by name A to Z
    When I sort products by "Name (A to Z)"
    Then the first product name should be "Sauce Labs Backpack"

  Scenario: Products can be sorted by name Z to A
    When I sort products by "Name (Z to A)"
    Then the first product name should be "Test.allTheThings() T-Shirt (Red)"

  Scenario: Products can be sorted by price low to high
    When I sort products by "Price (low to high)"
    Then the first product name should be "Sauce Labs Onesie"

  Scenario: Products can be sorted by price high to low
    When I sort products by "Price (high to low)"
    Then the first product name should be "Sauce Labs Fleece Jacket"

  @smoke
  Scenario: Adding a product increments the cart badge
    When I add "Sauce Labs Backpack" to the cart from the inventory
    Then the cart badge should show "1"

  Scenario: Adding two products shows badge count of two
    When I add "Sauce Labs Backpack" to the cart from the inventory
    And I add "Sauce Labs Bike Light" to the cart from the inventory
    Then the cart badge should show "2"

  Scenario: Removing a product from the inventory page clears the badge
    When I add "Sauce Labs Backpack" to the cart from the inventory
    And I remove "Sauce Labs Backpack" from the cart from the inventory
    Then the cart badge should not be visible
