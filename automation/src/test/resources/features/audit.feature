@api @audit
Feature: Audit trail (inspired by 21 CFR Part 11 / ALCOA+)
  Every product change is recorded with who, when, old and new value, and the record cannot be edited.

  Scenario: A price change is recorded with who made it
    Given a product priced "10.00" with stock 5 exists
    When the admin changes the price of that product to "12.50"
    Then the audit trail shows "price" changed from "10.00" to "12.50" by the admin
    And the audit trail integrity check passes

  Scenario: Only the store admin can read the audit trail
    Given a new customer is registered and logged in
    When the customer opens the audit trail
    Then the response status should be 403
