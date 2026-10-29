# Roadmap to a live store

Status on 29 Oct 2026: phases 1, 2, 3 and 5 done (engineering), phase 4's engineering part done; product photo uploads done;
next: the owner's part of phases 0, 4 and 5, then phase 6. The owner decides phase 0; engineering does phases 1-3 and 5; phase 4 needs
an accountant and a lawyer. Running costs are estimates to check against each provider's own price page.

## 1. What exists and what a live store still needs

| Area | In the code today | Needed before launch | Who |
|---|---|---|---|
| Card payments | Stripe Elements and signed webhooks (phase 2, done); test mode until the owner's live keys | Stripe Elements in the browser (the card goes straight to Stripe, never to our server), webhooks for payment results, 3-D Secure, live keys | Engineering; owner opens the Stripe account |
| Sales tax | Stripe Tax by delivery address (phase 3, done); flat 6 % until switched on | correct rate by state and product (for example Stripe Tax), Pennsylvania sales tax license | Engineering; owner + accountant |
| Shipping | Shippo labels and carrier tracking (phase 3, done); made-up numbers until switched on | carrier rates and labels (for example Shippo or EasyPost) | Engineering; owner opens the account |
| E-mail | Amazon SES from the store's domain, bounce suppression (phase 3, done); Mailpit until the domain exists | a mail service, the store's own domain, SPF and DKIM | Engineering + owner (domain) |
| Products | 10 sample products with drawn pictures; photo upload from the admin form ([ADR-0017](../adr/0017-product-photo-uploads.md), done) | real products, photos, descriptions, suppliers | Owner |
| Hosting | demo server (nightly reset) and a production stack with backups, restore drill and alerts (phase 5) | a second server, the domain, a backup bucket | Owner |
| Legal | policy pages and cookie notice (drafts, phase 4) | business registration (LLC, EIN), sales tax license, a lawyer's review of the policies | Owner + lawyer |
| Customer service | none | contact form, response times | Owner |
| Search engines | none | page titles and descriptions, sitemap, Search Console | Engineering |
| Already strong | orders, no overselling, returns and refunds, coupons, accounts with two-factor sign-in, audit trail, six languages, 1,800+ tests, 19 CI gates | the same test rules for every new feature | - |

The most urgent change is card handling. Today the card number reaches the backend, which is acceptable for test cards
only; with real cards it would put the server under the full PCI DSS card-data rules. Stripe Elements removes that.
Phase 2 does exactly that (ADR-0011).

## 2. Phases

1. **Phase 0 - decide (owner, 1-2 weeks):** what to sell, where it comes from, the brand. Nothing else can be planned in detail without it.
2. **Phase 1 - foundation (engineering, 1-2 days):** this repository as an independent unit with the full history of its code, its links and project names pointing here, this roadmap and ADR-0010.
3. **Phase 2 - safe card payments (engineering, 1-2 weeks):** Stripe Elements, webhooks, refunds, all tested in Stripe test mode with the existing contract-test layers.
   *Built:* Stripe Elements card form, signed webhooks, exactly-once settlement, live keys refuse card numbers
   ([ADR-0011](../adr/0011-stripe-elements-and-webhooks.md), [test-mode guide](../modules/STRIPE_ELEMENTS_BN.md)). Merged.
   Left for the owner: a manual run in Stripe test mode with `stripe listen` before live keys are used.
4. **Phase 3 - tax, shipping, e-mail (engineering, 2-3 weeks):** real tax calculation, carrier rates and labels, mail from the store's domain.
   *Chosen:* Stripe Tax, Shippo, Amazon SES; the domain is a placeholder until the owner buys one.
   *Done (Oct 2026):* Stripe Tax behind `TAX_PROVIDER` ([ADR-0012](../adr/0012-stripe-tax.md), [guide](../modules/STRIPE_TAX_BN.md));
   Shippo labels and carrier tracking behind `SHIPPING_PROVIDER` ([ADR-0013](../adr/0013-shippo-labels-and-tracking.md),
   [guide](../modules/SHIPPO_SHIPPING_BN.md)); order e-mail from the store's domain through Amazon SES with bounce and
   complaint suppression ([ADR-0014](../adr/0014-amazon-ses-mail.md), [guide](../modules/AMAZON_SES_MAIL_BN.md)).
   Left for the owner: buy the domain, verify it in SES, request SES production access.
5. **Phase 4 - business and legal (owner and professionals, alongside phases 2-3):** LLC and EIN, sales tax license, business bank account, Stripe live verification; engineering builds the policy pages, a lawyer reviews them.
   *Engineering part built:* privacy policy, terms, return and shipping policies that quote the shop's own settings, a
   cookie notice, agreement links at sign-up and checkout ([ADR-0015](../adr/0015-policies-from-the-shops-settings.md)).
   *Owner's part, step by step with fees (Bangla):* [PHASE4_BUSINESS_LEGAL_BN.md](../modules/PHASE4_BUSINESS_LEGAL_BN.md).
6. **Phase 5 - production server (engineering, 1 week):** demo mode off, daily backups, alerts, a restore drill.
   *Built:* a separate production stack (profile prod, demo off, required settings), daily encrypted backups copied
   off the server, a weekly restore drill, a restore command, a 5-minute health check with alerts, all proven by the CI
   job "Production stack" ([ADR-0016](../adr/0016-production-stack-backups-and-alerts.md),
   [guide (Bangla)](../deploy/PRODUCTION_BN.md)). Left for the owner: the server, the domain, the bucket, the phone app.
   *Also built before launch:* product photo upload from the admin form: re-encoded by the backend (upright, at most
   1600 px, no EXIF/GPS), stored in the database so the backups cover it ([ADR-0017](../adr/0017-product-photo-uploads.md),
   [guide (Bangla)](../modules/PRODUCT_PHOTOS_BN.md)). Still before launch (engineering): a contact form, page titles
   and a sitemap for search engines.
7. **Phase 6 - UAT and a quiet start (owner and engineering, 2 weeks):** the existing [UAT plan](../qa/UAT_PLAN.md) with family and friends paying small real orders that are then refunded; GO opens the store.

## 3. Running costs (estimates)

| Cost | About | When |
|---|---|---|
| Domain | $10-20 a year | phase 1-3 |
| Server | $0 on Oracle's free tier; $10-25 a month rented | phase 5 |
| Stripe card payments | 2.9 % + 30 cents per US card payment, no monthly fee | per sale |
| Tax calculation service (Stripe Tax) | about 0.5 % per transaction | per sale |
| E-mail service | $0-15 a month for a small store | phase 3 |
| Shipping labels | the carrier's rate per parcel | per sale |
| Business registration, legal review | one-time state and professional fees | phase 4 |

For comparison, a hosted store on Shopify Basic is $39 a month, or $29 a month paid yearly, and covers phases 2, 3 and 5
itself ([NerdWallet, March 2026](https://www.nerdwallet.com/article/small-business/shopify-pricing)). Running our own code
costs less each month and keeps full control, but the owner carries more responsibility and time.
