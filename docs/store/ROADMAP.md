# Roadmap to a live store

Status on 25 Oct 2026: phase 1 in progress. The owner decides phase 0; engineering does phases 1-3 and 5; phase 4 needs
an accountant and a lawyer. Running costs are estimates to check against each provider's own price page.

## 1. What exists and what a live store still needs

| Area | In the code today | Needed before launch | Who |
|---|---|---|---|
| Card payments | Stripe PaymentIntents and refunds, but only Stripe test cards, through the shop's own card form | Stripe Elements in the browser (the card goes straight to Stripe, never to our server), webhooks for payment results, 3-D Secure, live keys | Engineering; owner opens the Stripe account |
| Sales tax | one flat rate (6 %) on every order | correct rate by state and product (for example Stripe Tax), Pennsylvania sales tax license | Engineering; owner + accountant |
| Shipping | made-up tracking numbers, flat $5.99 | carrier rates and labels (for example Shippo or EasyPost) | Engineering; owner opens the account |
| E-mail | delivered into Mailpit (test inbox) | a mail service, the store's own domain, SPF and DKIM | Engineering + owner (domain) |
| Products | 10 sample products with drawn pictures | real products, photos, descriptions, suppliers | Owner; engineering adds photo upload |
| Hosting | one free Oracle server, demo mode, nightly reset | demo mode off, daily backups, monitoring and alerts, a tested restore | Engineering |
| Legal | none | business registration (LLC, EIN), privacy policy, terms, return policy, cookie notice | Owner + lawyer; engineering builds the pages |
| Customer service | none | contact form, response times | Owner |
| Search engines | none | page titles and descriptions, sitemap, Search Console | Engineering |
| Already strong | orders, no overselling, returns and refunds, coupons, accounts with two-factor sign-in, audit trail, six languages, 1,489 tests, 18 CI gates | the same test rules for every new feature | - |

The most urgent change is card handling. Today the card number reaches the backend, which is acceptable for test cards
only; with real cards it would put the server under the full PCI DSS card-data rules. Stripe Elements removes that.
`StripePaymentGateway.java` already says so in its header.

## 2. Phases

1. **Phase 0 - decide (owner, 1-2 weeks):** what to sell, where it comes from, the brand. Nothing else can be planned in detail without it.
2. **Phase 1 - foundation (engineering, 1-2 days):** this repository with the full history of the E-Commerce Suite, its links pointing here, this roadmap and ADR-0010.
3. **Phase 2 - safe card payments (engineering, 1-2 weeks):** Stripe Elements, webhooks, refunds, all tested in Stripe test mode with the existing contract-test layers.
4. **Phase 3 - tax, shipping, e-mail (engineering, 2-3 weeks):** real tax calculation, carrier rates and labels, mail from the store's domain.
5. **Phase 4 - business and legal (owner and professionals, alongside phases 2-3):** LLC and EIN, sales tax license, business bank account, Stripe live verification; engineering builds the policy pages, a lawyer reviews them.
6. **Phase 5 - production server (engineering, 1 week):** demo mode off, daily backups, alerts, a restore drill.
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
