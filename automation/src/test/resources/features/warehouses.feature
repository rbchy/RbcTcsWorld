@api @inventory
Feature: Multi-warehouse inventory
  As the store I keep stock in several warehouses. An order ships from as few warehouses as possible,
  and cancelled units go back exactly where they came from. The total never goes wrong.

  Background:
    Given a new customer is registered and logged in
    And a product priced "4.00" with stock 3 exists

  @smoke
  Scenario: A line bigger than one warehouse is split, and cancel puts every unit back
    Given warehouse "NWK" receives 2 units of that product
    And the customer has 4 of that product in the cart
    When the customer places the order
    Then the response status should be 201
    And the order ships 3 from "PIT" and 1 from "NWK"
    When the customer cancels the order
    Then warehouse "PIT" holds 3 and warehouse "NWK" holds 2 of that product
    And the product stock should be 5

  Scenario Outline: Stock moves between warehouses are checked
    When the admin moves <quantity> units of that product from "<from>" to "<to>"
    Then the response status should be <status>

    Examples:
      | from | to  | quantity | status |
      | PIT  | NWK | 3        | 200    |
      | PIT  | NWK | 4        | 409    |
      | PIT  | PIT | 1        | 400    |

  Scenario: Damaged units are written off and the total follows
    When the admin writes off 1 damaged unit of that product in "PIT"
    Then the response status should be 200
    And the product stock should be 2
