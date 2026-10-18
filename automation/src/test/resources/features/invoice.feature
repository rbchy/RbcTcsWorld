@api @order
Feature: Order invoice as a PDF
  A customer can download the invoice of their own order as a PDF file.

  Background:
    Given a new customer is registered and logged in
    And a product priced "12.50" with stock 5 exists
    And the customer has 2 of that product in the cart
    And the customer has placed the order

  Scenario: The invoice is a PDF with the order number and the total
    When the customer downloads the invoice of the order
    Then the response status should be 200
    And the response is a PDF invoice with the total "$32.49"

  Scenario: Another customer cannot download it
    Given a new customer is registered and logged in
    When the customer downloads the invoice of the order
    Then the response status should be 404
