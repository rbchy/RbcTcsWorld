# ADR 0026: STORE_SALES=off runs the site as an Amazon affiliate site, with nothing to buy here

- **Status:** Accepted
- **Date:** 2026-11 (affiliate first, own shop later)

## Context
The owner starts with Amazon Associates (ADR-0020) and opens the own shop later. A live site with the shop switched
on but no Stripe account would run with the mock payment gateway: anyone could "pay" with the test card 4242 and
believe they had ordered. Amazon's reviewers also reject sites that look half-built, and the Associates rules ask
the privacy policy to name third parties, Amazon included, that may set cookies.

## Decision
- **One setting, `STORE_SALES` = `on` (default) | `off`.** A typo stops the start. `deploy/production/.env.example`
  starts with `off`; the full shop is one change away.
- **Backend, `off`:** cart, checkout, orders (with returns and invoices), payments, saved cards, guest checkout and
  "move to cart" answer 409 with one plain sentence (an interceptor after the security rules, so no login is still
  401). Visitors and customers see only products with a partner link: catalog, facets, categories, deals, similar
  and bought-together lists, product and variant pages (others 404); the sitemap lists only them and leaves out the
  shipping and return policies. The admin sees and edits everything.
- **Storefront, `off`** (`GET /api/store/mode`): no cart icon, no cart/orders/addresses/wallet menu, no welcome
  coupon or deals shelf, an affiliate line instead of shipping promises, no shipping or return policy links; those
  pages and the shop pages show "Not sold here yet". Privacy policy and terms say the site does not sell its own
  products yet.
- **Privacy policy, both modes:** Amazon and partner shops are listed among those who see data, and the Associates
  statement: third parties, including Amazon, may provide content and advertising, collect information directly
  from visitors, and place or recognize cookies on visitors' browsers.

## Consequences
- The site can go live for Amazon Associates without Stripe, Shippo, SES, an LLC or a sales tax license.
- Accounts and wishlists keep working (sign-up is free and harmless); password-reset e-mails need SES, or they stay in
  the log (`NOTIFICATIONS_CHANNEL=log`) until the domain is verified.
- Proven by `PartnerOnlyModeIntegrationTest`, `StoreSalesTest`, `SeoServiceTest`, the access-control matrix and
  `playwright/tests/partner-site.spec.ts` (REQ-AFF-04).
