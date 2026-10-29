# ADR 0019: Real addresses, page tags and a sitemap for search engines, without leaving the hash router

- **Status:** Accepted
- **Date:** 2026-10 (the "Search engines" row of the roadmap)

## Context
The storefront is a single-page app with a hash router: every page lives at `/#/...`. Search engines ignore
everything after `#`, so for Google the whole shop was one page called "RbcTcsWorld Shop" with the description
"storefront and store admin". A live store needs each product to be findable on its own, with its name, price and
picture, and the private pages (cart, orders, admin) and the public demo must never show up in results.

Moving the whole app to path routing would change every link and about 80 places in the browser test suites that
open or check `#/` addresses, for no gain to customers.

## Decision
- **Real paths open the same pages.** When there is no `#`, the router reads the path: `/products/12/travel-mug`,
  `/contact`, `/help`, `/privacy` ... show exactly what `#/products/12` etc. show. nginx and Vite already answer every
  unknown path with the app. Links inside the shop stay `#/` links.
- **One canonical address per page.** Public pages write `<link rel="canonical">` with their real path; a product's
  is `/products/<id>/<slug>`, and every size and colour of an item points to the item's first variant, the same
  address the sitemap lists. The slug is computed identically in the backend (`SeoService.productPath`) and the
  frontend (`head.js`).
- **Title, description and robots tag on every page** (`head.js`): "Name | RbcTcsWorld Shop", a description in the
  visitor's language (product: name and current price), `index, follow` for the catalog, products, contact, help and
  policies; `noindex` for cart, checkout, orders, account pages, sign-in and admin.
- **schema.org Product data** (JSON-LD) on product pages: name, SKU, category, picture, price in USD (the deal price
  while a deal runs), availability, and the review average when there are reviews. This is what search engines use
  for price and stock in results.
- **`/robots.txt` and `/sitemap.xml` from the backend** (`/api/seo/...`, served at the root by nginx and Vite): the
  sitemap lists the public pages and one address per item on sale, with its picture. robots.txt names the sitemap,
  keeps `/api/admin/` and `/api/auth/` out, and leaves `/api/products` open, because search engines run the page's
  JavaScript and need the catalog API to see a product.
- **Never indexed:** the public demo (demo mode: robots.txt `Disallow: /`, and the demo build writes `noindex` on
  every page) and any shop whose `PUBLIC_URL` is not `https://` (development, CI).

## Consequences
- Search engines that run JavaScript (Google, Bing) see full product pages. Link previews in chat apps and social
  networks do not run JavaScript and still show the general title; server-rendered tags would be the next step if
  that matters.
- Inside the shop the address bar keeps showing `#/` addresses; a link copied from there still works, it just is
  not the canonical one.
- Category pages are not in the sitemap: the category filter is not an address of its own yet.
- The smoke test checks robots.txt and the sitemap on the live store and the demo; an older demo server needs one
  redeploy for that check to pass.
- Proven by `SeoServiceTest`, `SeoIntegrationTest`, the access-control matrix, `playwright/tests/seo.spec.ts` and
  `deploy/smoke.sh` in the "Production stack" and "Public demo stack" CI jobs (REQ-SEO-01..03).
