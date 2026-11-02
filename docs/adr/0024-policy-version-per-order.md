# ADR 0024: Every order records the version of the terms and policies it was placed under

- **Status:** Accepted
- **Date:** 2026-11 (before launch)

## Context
The policy pages show an effective date (ADR-0015), and the terms say a change "applies to orders placed after its
effective date". But no order kept that date: after a change, nobody could prove which return window, refund rule or
terms a customer had agreed to. In a dispute (a chargeback, a complaint to a state attorney general) that proof is
what counts.

## Decision
- **Checkout records it:** each new order stores the policies' effective date and whether they were lawyer-reviewed
  or still drafts at that moment (V36 `orders.policies_effective`, `policies_reviewed`), taken from the same settings
  the policy pages show (`STORE_POLICIES_EFFECTIVE`, `STORE_POLICIES_REVIEWED`, through `PolicyVersion`).
- **Shown where it matters:** the order API (`policies: {effective, reviewed}`), the customer's order page ("Placed
  under the terms and policies effective ...") and the invoice PDF ("... (draft)" while unreviewed).
- **Older orders** keep `null`: they were placed before the record existed, and inventing a date would be wrong.
- The texts themselves stay in the code (`Legal.jsx`): git history is the archive of every version. A change of the
  texts must come with a new `STORE_POLICIES_EFFECTIVE`, which the owner's lawyer checklist says.

## Consequences
- A dispute can be answered from the order alone: its date names the exact text in git.
- The full text is not copied into each order; that would bloat every row for a rare need.
- Proven by `OrderServiceTest`, `InvoicePdfTest`, `OrderPolicyVersionIntegrationTest` and the order contract schema
  (REQ-LEG-04).
