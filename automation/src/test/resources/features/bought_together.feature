@api @catalog
Feature: Customers who bought this also bought
  The product page recommends what other customers put in the same order, most often first.

  Scenario: The product bought most often with this one comes first
    Given a product priced "10.00" with stock 50 exists
    And another product priced "11.00" with stock 50 exists
    And 2 customers each bought both products in one order
    When the shopper asks what was bought together with the first product
    Then the response status should be 200
    And the first recommendation is the second product

  Scenario: A product nobody bought has no recommendations
    Given a product priced "10.00" with stock 50 exists
    When the shopper asks what was bought together with the first product
    Then the response status should be 200
    And there are no recommendations

  Scenario: A limit above 12 is refused
    Given a product priced "10.00" with stock 50 exists
    When the shopper asks for 13 recommendations for the first product
    Then the response status should be 400
