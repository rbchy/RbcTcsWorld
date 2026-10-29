# ADR 0020: Affiliate (partner) products next to the shop's own, as a link instead of a cart

- **Status:** Accepted
- **Date:** 2026-10

## Context
Before the owner has decided what to sell and found suppliers (roadmap phase 0), the store can start earning by
recommending products that a partner shop sells, for example through the Amazon Associates program: the customer
buys at Amazon, the shop earns a commission. The owner wants this for electronics, shoes and clothing first.

The partner's rules shape the feature (Amazon Associates Operating Agreement; FTC endorsement guides):
- Amazon prices may only be shown from Amazon's own API and at most 24 hours old; the API (now the Creators API) is
  only granted after the account has made sales. Product pictures may not be copied from Amazon either.
- Every page with an Amazon link must say "As an Amazon Associate I earn from qualifying purchases" (or similar)
  where readers see it before the link; a footer line alone is not enough for the FTC.
- Affiliate links may not be put in e-mails or offline material.
- Paid links must be marked for search engines (`rel="sponsored"`).

## Decision
- **One optional field per product, `affiliateUrl`** (https only, up to 500 characters, migration V29). Empty: an
  ordinary product the shop sells. Set: a partner product. It goes through the same form, audit trail, variants,
  categories, photos, search and sitemap as every other product.
- **Never in our cart or orders:** adding a partner product to the cart is 400; a product that became a partner
  product while in someone's cart shows as unavailable and stops the order (400, "remove it from the cart").
- **The storefront shows no price for a partner product** and no stock: the card and the product page show "Buy at
  Amazon" (the shop's name taken from the link), opening in a new tab with `rel="sponsored nofollow noopener
  noreferrer"`. The disclosure sits directly above the button on the product page and above the product grid in the
  catalog, with Amazon's own sentence when an Amazon link is on the page. The structured data for search engines
  carries no offer (we neither sell it nor show its price), and the description says where it is available.
- **Pictures:** the owner uses their own photos (photo upload, ADR-0017) or the category picture; the form says so.
  A "shoes" category picture was added.
- **The terms of service** say that partner products are bought from the partner, under its terms, and that the
  shop may earn a commission.
- **Not in the public demo:** like outside picture links, partner links are refused there (anybody can be the demo's
  admin and could point visitors anywhere).
- **E-mails never contain partner links:** partner products cannot be ordered, so no order e-mail mentions them.
  The owner must not paste them into answers to contact messages either (the guide says so).

## Consequences
- The price field stays required for every product. For a partner product it is the owner's own note: it is never
  shown, but it still counts in the price filter and the price sorting of the catalog.
- When the Amazon account qualifies for the Creators API, the next step is to fetch price, availability and the
  picture from it (at most 24 hours old) and show them; until then the page shows none.
- Clicks are counted by Amazon's own reports, not by the shop.
- Proven by `CartServiceTest`, `OrderServiceTest`, `ProductImagePolicyTest`, `AffiliateIntegrationTest`, the demo
  test and `playwright/tests/affiliate.spec.ts` (REQ-AFF-01..03).
