# ADR 0006: Payments behind a gateway interface; mock by default, Stripe contract-tested

- **Status:** Accepted
- **Date:** 2026-10 (PR #20)

## Context
The shop needs realistic payments (approve, decline with a reason, refund) in every test run. It must never
need a real card, and a test run must never be able to move real money.

## Decision
- `PaymentGateway` interface with two implementations. `PAYMENT_PROVIDER` chooses between them.
  - **mock** (default): test cards 4242… approve, 4000…0002 decline.
  - **stripe**: `java.net.http` client, amounts in cents, idempotency keys per order and attempt.
- Card errors map to HTTP 402 with a short, customer-safe reason. Provider problems map to 503.
- A **live** Stripe key is refused unless the `prod` profile is active. The public demo always uses mock.
- **Three test layers** for Stripe:
  - stripe-mock (Stripe's own mock server) on every build;
  - recorded Stripe answers for exact error parsing;
  - the real Stripe sandbox when a test key is configured.

## Alternatives considered
- **Stripe Java SDK:** a large dependency, and an extra layer between the tests and the HTTP request.
- **Stripe Elements / Checkout:** the right choice for a real shop (card data never touches the server). Listed
  as a known limitation, because the shop's own card form is easier to test end to end.

## Consequences
- 0 double charges by construction (idempotency). Every build checks the contract against stripe-mock.
- Webhooks (refunds or disputes made in the Stripe dashboard) are not handled yet. This is on the roadmap.
