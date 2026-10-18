@api @catalog
Feature: Faceted search
  The filter panel on the left of the catalog shows, for every option, how many items would be found, like Amazon.
  Counts are items (catalog cards), not variants, and a filter group ignores its own selection.

  Scenario: The seeded T-shirt counts once, with both colours
    When the shopper asks for the filter counts of "Classic Cotton T-Shirt"
    Then the filter panel counts 1 item
    And the colour "Navy" is offered with 1 item

  Scenario: Choosing a colour still offers the other colour
    When the shopper asks for the filter counts of "Classic Cotton T-Shirt" in the colour "Navy"
    Then the filter panel counts 1 item
    And the colour "Heather Grey" is offered with 1 item

  Scenario: A rating above 5 is refused
    When the shopper asks for the filter counts with a minimum rating of 6
    Then the response status should be 400
    And the error message should contain "minRating must be between 0 and 5"
