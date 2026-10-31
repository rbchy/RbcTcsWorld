# ADR 0012: Sales tax from Stripe Tax behind a TaxCalculator interface

- **Status:** Accepted
- **Date:** 2026-10 (store Phase 3, part 1 of 3)

## Context
Every order paid a flat 6 % on the merchandise. A real US store owes the rate of the delivery address (state, county,
city, special districts), some products are taxed differently, some states tax shipping, and every sale must be in a
report for filing. Keeping such a table ourselves is a full-time job.

## Decision
- **`TaxCalculator` interface**, chosen by `TAX_PROVIDER`:
  - `flat` (default): the old 6 % on the merchandise, so every existing test, the demo and all figures stay the same.
  - `stripe`: Stripe Tax, on the same Stripe account and key as payments (one vendor, one contract, about 0.5 % per sale).
- **What Stripe Tax gets:** every line (SKU, quantity, amount after the order discount, spread over the lines by amount,
  rounding rest on the last line), the shipping cost, and the delivery address. Prices are tax-exclusive, as US shops show.
- **Never a guessed tax on an order.** Without an address (the price preview) the flat rate gives an estimate and the
  API says so (`taxEstimated`). When the order is placed the address is known; an address Stripe cannot place is 400,
  any other Stripe failure is 503, both before anything is saved, reserved or cleared.
- **The sale is recorded after payment.** The calculation id is kept on the order (`tax_calculation_id`). After the
  payment commits, a listener records it with Stripe (`create_from_calculation`, idempotency key from the order number).
  An outage there is logged and never fails the payment: the money has already moved.
- Same start rules as payments: a typo, a missing key, or a live key outside the `prod` profile stops the start.

## Consequences
- Correct tax for every US address with no table to maintain; the Stripe Dashboard has the filing report.
- Placing an order needs Stripe to answer when `TAX_PROVIDER=stripe`. That is accepted, because the alternative is a
  wrong tax on a real invoice.
- **Refunds reverse the tax** (REQ-PAY-12): the recorded sale's id is kept on the order (V31). A paid order cancelled
  reverses the whole sale; an approved return reverses the refunded amount (Stripe's `flat_amount`, tax included, spread
  over the sale's lines and shipping). Like the sale, the reversal is sent after the refund commits, once (the
  reference `<order number>-cancel` / `-return` is the idempotency key), and an outage is logged, never undoing the refund.
- **Product tax codes** (REQ-PAY-13): a product may carry its Stripe Tax code (V33, e.g. `txcd_30011000` clothing and
  footwear), sent with each of its lines; products without one use the account's default (general tangible goods).
  The admin form suggests the common codes; the full list is Stripe's.
- The owner must register in each state where the store collects tax (Phase 4); Stripe only calculates where the account
  has a registration.
