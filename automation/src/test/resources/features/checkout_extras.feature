@api @checkout
Feature: Guest checkout and the address book
  Shoppers can buy without creating an account, and customers keep their delivery addresses.

  Scenario: A guest buys without a password
    Given a guest starts a checkout with a new e-mail
    When the guest orders 1 of a new product and pays
    Then the order status should be "PAID"

  Scenario: The same guest e-mail twice is sent to Forgot password
    Given a guest starts a checkout with a new e-mail
    When a guest starts a checkout with the same e-mail again
    Then the response status should be 409
    And the error message should contain "Forgot password"

  Scenario: The first saved address is the default until another one is chosen
    Given a new customer is registered and logged in
    When the customer saves the address "1 Main St" as "Home"
    And the customer saves the address "9 Work Rd" as "Office"
    Then the default address is "Home"
    When the customer makes "Office" the default
    Then the default address is "Office"

  Scenario: The same address cannot be saved twice
    Given a new customer is registered and logged in
    And the customer saves the address "1 Main St" as "Home"
    When the customer saves the address "1 MAIN ST" as "Again"
    Then the response status should be 409
