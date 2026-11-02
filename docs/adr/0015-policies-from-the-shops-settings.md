# ADR 0015: Store policies quote the shop's own settings and stay drafts until reviewed

- **Status:** Accepted
- **Date:** 2026-10 (store Phase 4, engineering part)

## Context
A live store needs a privacy policy, terms, a return policy and a shipping policy. The usual way is to paste text from a
generator. That text drifts from what the shop really does: a policy that promises 30 days while the code allows 14 is
a legal problem, and a policy with someone else's company name is worse. The business does not exist yet (no LLC, no
address), and no lawyer has read anything.

## Decision
- **Four public pages** in the storefront (`#/privacy`, `#/terms`, `#/returns-policy`, `#/shipping-policy`), linked from
  every page's footer, in English (the business language; other languages say so).
- **Numbers from the code's settings:** `GET /api/store/legal` (public) returns the return window, shipping fee and
  free-shipping threshold from `app.returns.*` and `app.pricing.*`, and the business details from `STORE_*`. A changed
  setting changes the policy; a test compares the page with the API.
- **Honest about what the shop does:** the texts name the actual processors (Stripe, Shippo and the carriers, Amazon
  SES), the actual browser storage (sign-in and language only; Stripe's own fraud cookies on the card form), the actual
  refund table (ReturnPolicy) and what is never stored (full card numbers, CVV).
- **Draft until proven otherwise:** every page shows placeholders and a "Draft" note until all business details are set
  and `STORE_POLICIES_REVIEWED=true`; "reviewed" is forced to false while any detail is missing.
- **Cookie notice, not a consent form:** only strictly necessary storage is used, so a notice that says so is enough.
  It sits in the page flow under the content, above the footer: never over anything, and nothing above it moves
  (a first version above the header pushed the sticky header over menu links; CI caught it). "OK" hides it for good.
- Account creation and checkout name the terms and policies they agree to.

## Consequences
- The owner's lawyer reviews one short set of texts that already match the shop; changes go into `Legal.jsx`.
- Customers delete their own account from Security ([ADR-0022](0022-self-service-account-deletion.md)).
- Every order records the policies' effective date it was placed under ([ADR-0024](0024-policy-version-per-order.md)).
