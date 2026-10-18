@api @catalog
Feature: Product variants (size and colour)
  Like Amazon's parent and child items, every size and colour is its own product with its own SKU, price and
  stock; products that share a variant group are the options of one item.

  Scenario: The seeded T-shirt is one catalog card with eight options
    When the shopper opens the grouped catalog for "Classic Cotton T-Shirt"
    Then the catalog shows 1 card
    And that card's item has 8 options in the colours "Navy, Heather Grey" and the sizes "S, M, L, XL"

  Scenario: The same size and colour cannot be added twice to one group
    Given the store admin adds a "Hoodie" in group "HOOD" with size "M" and colour "Red"
    When the store admin adds a "Hoodie" in group "HOOD" with size "m" and colour "RED"
    Then the response status should be 409
    And the error message should contain "already has"

  Scenario: A size without a variant group is refused
    When the store admin adds a "Hoodie" in group "" with size "M" and colour ""
    Then the response status should be 400
    And the error message should contain "needs a variant group"

  Scenario: The order line names the size and colour that were bought
    Given the store admin adds a "Beanie" in group "BEANIE" with size "" and colour "Olive"
    And a new customer is registered and logged in
    When the customer orders 2 of that variant
    Then the order line is named "Beanie (Olive)"
