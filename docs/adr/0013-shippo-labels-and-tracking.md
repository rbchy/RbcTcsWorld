# ADR 0013: Shipping labels and tracking from Shippo behind a ShippingProvider interface

- **Status:** Accepted
- **Date:** 2026-10 (store Phase 3, part 2 of 3)

## Context
"Ship" made up a tracking number such as `UPS-482910384756`. A real store needs a label it can print, from a real
carrier, paid at the carrier's rate, and the order should become DELIVERED when the carrier delivers it, not when
someone remembers to click.

## Decision
- **`ShippingProvider` interface**, chosen by `SHIPPING_PROVIDER`:
  - `simulated` (default): the made-up tracking number, no label, no money. Every existing test and the demo stay the same.
  - `shippo`: Shippo, one account for USPS, UPS and FedEx, with free test labels (`shippo_test_...` token).
- **What the customer pays does not change.** Shipping on the order stays the store's price ($5.99, free from $50).
  The label cost is the store's cost, kept on the order (`label_cost`) for the margin.
- **Buying a label:** one parcel per order, rated from the store's return address to the delivery address. Its weight
  and size come from the products (V32, REQ-FUL-06): each unit's own packed weight, or 16 oz per item where none is
  given; a single unit with its own box ships in that box, otherwise the default box (10 x 8 x 4 in, from config) is
  grown side by side, longest to longest, until every product's own box fits. The cheapest rate of the carrier the admin chose is bought. The real
  tracking number replaces the made-up one; the label PDF link, cost and Shippo's id are kept on the order.
- **Only a PAID order gets a label**, checked before Shippo is called. No rate for that carrier or an address the
  carrier refuses is 400; Shippo down is 503. Either way no label is bought, the order stays PAID, nothing is announced.
- **Delivered by the carrier:** Shippo's `track_updated` webhook. Shippo does not sign webhooks, so the URL carries a
  secret (`SHIPPO_WEBHOOK_TOKEN`, constant-time compare; none configured 503). The body only names the parcel: the
  status is read back from Shippo, and only DELIVERED moves a SHIPPED order to DELIVERED. A forged call cannot deliver
  an order, and repeats change nothing.
- Same start rules as payments and tax: a typo, a missing token, or a live token outside `prod` stops the start.

## Consequences
- The warehouse prints a real label from the admin's order list ("Label" -> "Print label ($7.58)").
- Not done yet, needed before live shipping:
  - The parcel size for several items is an estimate: each side is big enough for the biggest product, but the
    volume of many items together is not added up. Products without a weight or box still use the defaults, and a
    wrong weight can cost a carrier adjustment.
  - The return address is a placeholder until the store has one (`SHIP_FROM_*`). An order split over several
    warehouses still ships as one parcel from that address.
  - If the database fails right after Shippo sold the label, the order stays PAID and the label is unused. Shippo
    refunds unused labels on request; the log line has the order number.
  - Return labels: bought by the admin for an approved return ([ADR-0023](0023-prepaid-return-labels.md)).
