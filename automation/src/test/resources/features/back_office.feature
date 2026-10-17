@api @admin
Feature: Store admin back office
  The store admin manages customer accounts and coupons, and reads the reports.

  Scenario: A disabled customer cannot log in until the account is enabled again
    Given a customer account exists
    When the admin disables that customer
    Then that customer cannot log in and is told the account is disabled
    When the admin enables that customer
    Then that customer can log in again

  Scenario: An edited coupon keeps its code
    Given the admin has created a "FIXED" coupon worth "5.00"
    When the admin changes that coupon to "PERCENT" worth "15"
    Then the response status should be 200
    And the coupon code is unchanged and its type is "PERCENT"

  Scenario Outline: Every report answers with a header and rows
    When the admin opens the "<report>" report
    Then the response status should be 200
    And the report is named "<report>" and has columns

    Examples:
      | report             |
      | sales              |
      | top-products       |
      | orders-by-status   |
      | returns            |
      | coupons            |
      | low-stock          |
      | inventory          |
      | stock-by-warehouse |
