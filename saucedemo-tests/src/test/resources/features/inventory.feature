@inventory @smoke
Feature: SauceDemo Inventory
  As a logged-in shopper
  I want to browse and filter products
  So that I can find what I want to buy

  Background:
    Given I am logged in as "standard_user"
    And I am on the inventory page

  @happy-path
  Scenario: All six products are displayed
    Then I should see 6 products listed
    And each product should have a name, price, and add-to-cart button

  @sorting
  Scenario: Sort products by name A to Z
    When I sort products by "Name (A to Z)"
    Then the products should be sorted alphabetically ascending

  @sorting
  Scenario: Sort products by price low to high
    When I sort products by "Price (low to high)"
    Then the products should be sorted by price ascending

  @cart
  Scenario: Add a product to the cart from the inventory
    When I add "Sauce Labs Backpack" to the cart
    Then the cart badge should show "1"
    And the "Sauce Labs Backpack" button should change to "Remove"

  @cart
  Scenario: Add multiple products to the cart
    When I add "Sauce Labs Backpack" to the cart
    And I add "Sauce Labs Bike Light" to the cart
    Then the cart badge should show "2"
