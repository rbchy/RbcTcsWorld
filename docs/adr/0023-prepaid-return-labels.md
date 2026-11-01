# ADR 0023: Prepaid return labels from Shippo, bought by the admin for an approved return

- **Status:** Accepted
- **Date:** 2026-11 (before launch)

## Context
An approved return refunds the customer at once (ReturnPolicy), but the customer then had to find a carrier, pay and
type the store's address themselves (ADR-0013, "not done yet"). Parcels came back late, wrongly addressed or not at
all, and the customer paid for the shop's mistake on a wrong or damaged item.

## Decision
- **Admin -> Returns -> Buy return label** on an APPROVED return (`POST /api/admin/returns/{id}/label`), once.
  A separate step after the approval: the refund never waits for a carrier, and a Shippo outage cannot undo it.
- **From the customer's delivery address to the store's return address** (`SHIP_FROM_*`), the order's parcel as for
  the outbound label (products' own weight and box, ADR-0013), at **the cheapest rate of any carrier**: the customer
  can drop it at whichever is nearest. The label is bought at once (Shippo charges then; an unused label can be
  refunded in Shippo).
- **The customer** sees "Print return label" with the carrier and tracking number on the order page and gets an
  e-mail ("nothing to pay"). The store's cost and Shippo's id are kept on the return (V35) but never sent to the
  customer.
- 409 not approved or already bought; 400 when the provider cannot (simulated shipping, no rate, address refused);
  503 Shippo down. With simulated shipping the button explains that real labels need Shippo.

## Consequences
- The shop pays every return label, also for "no longer needed" returns, whose refund already leaves out the outbound
  shipping. The owner can simply not buy one for those.
- The return's tracking number is shown, but its delivery back to the store is not followed yet.
- Proven by `ShippoShippingProviderTest`, `ReturnServiceTest`, `ShippoShippingIntegrationTest`, the access-control
  matrix (REQ-RET-04).
