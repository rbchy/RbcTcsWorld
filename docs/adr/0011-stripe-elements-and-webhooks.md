# ADR 0011: Card payments through Stripe Elements; the order is paid only on Stripe's word

- **Status:** Accepted
- **Date:** 2026-10 (store Phase 2)

## Context
Until now the browser sent the card number to the shop, and the shop passed it on to the gateway. That is fine for the
mock provider and for Stripe test mode, but a live store that touches card numbers falls into the widest PCI DSS scope
(SAQ D). The live store must never see a card number. The order must also never be marked paid because the browser says
so: a customer can change any request the browser makes.

## Decision
- **Stripe Elements.** When `STRIPE_PUBLISHABLE_KEY` is set, the order page shows Stripe's card form (an iframe served by
  js.stripe.com). The shop creates a PaymentIntent for the order total (`POST /api/orders/{id}/card-payment`, idempotency
  key `order-{id}-elements-{cents}`) and hands only its client secret to the browser. The card goes from the browser to
  Stripe directly.
- **Stripe's word only.** The order becomes PAID when Stripe says the PaymentIntent succeeded: either the confirm call
  (`POST /api/orders/{id}/card-payment/confirm`) reads it back from Stripe, or Stripe sends a signed webhook
  (`POST /api/payments/stripe/webhook`). Whichever comes first settles it.
- **Webhook signature.** HMAC-SHA256 of `timestamp.body` with `STRIPE_WEBHOOK_SECRET`, constant-time comparison, at most
  5 minutes clock difference (no replays). No secret configured means 503, never "accept anything".
- **Exactly once.** `stripe_events` remembers each event id (Stripe retries until it gets a 2xx). `payment_intents.settled_at`
  is claimed with one atomic `UPDATE ... WHERE settled_at IS NULL`, so the confirm call and the webhook can race without
  paying twice.
- **Never the wrong amount.** A succeeded PaymentIntent whose amount differs from the order total, or that is not the
  intent stored for that order, does not pay the order. It is recorded as a failed payment with a reason.
- **A live key refuses card numbers.** With `sk_live_` the old endpoints (`/pay`, saving a card in the wallet) answer
  "Enter the card in the secure card form". A live secret key without a live publishable key does not start at all.
- The mock provider and Stripe test mode keep the shop's own card form, so all existing tests and the demo are unchanged.

## Consequences
- PCI scope drops to SAQ A for card entry: the shop's servers and logs never hold a card number.
- The storefront loads a script from js.stripe.com, so the Content-Security-Policy allows exactly Stripe's script, frame
  and API hosts and nothing else.
- Tests cannot use a real Stripe account in CI. The flow is covered with a mocked gateway (integration tests), recorded
  Stripe responses (contract tests), a known-answer HMAC test, and Playwright with a stand-in Stripe.js. The real
  end-to-end check is manual, in Stripe test mode with `stripe listen` (docs/modules/STRIPE_ELEMENTS_BN.md).
- A payment that is still processing leaves the order PLACED; the webhook settles it later. Without a webhook secret,
  such orders wait until the customer opens the order again.
