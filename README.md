<div align="center">

# 🛍️ RbcTcsWorld Shop

### An online store on its way to selling for real. Built test-first: 2,039 automated tests behind 19 CI quality gates, going live first as an Amazon affiliate site, with the own shop to follow

[![Typing SVG](https://readme-typing-svg.demolab.com/?font=Fira+Code&size=18&pause=1500&color=4F46E5&center=true&vCenter=true&width=900&repeat=true&lines=Java+21+%C2%B7+Spring+Boot+4.1+%C2%B7+PostgreSQL+16+%C2%B7+React;2%2C039+tests+%C2%B7+19+CI+gates+%C2%B7+97.5+%25+coverage;Stripe+%C2%B7+Stripe+Tax+%C2%B7+Shippo+%C2%B7+Amazon+SES;Affiliate+site+first+%C2%B7+own+shop+later)](https://git.io/typing-svg)

[![CI](https://github.com/rbchy/RbcTcsWorld/actions/workflows/ci.yml/badge.svg)](https://github.com/rbchy/RbcTcsWorld/actions/workflows/ci.yml)
[![Runner canary](https://github.com/rbchy/RbcTcsWorld/actions/workflows/runner-canary.yml/badge.svg)](https://github.com/rbchy/RbcTcsWorld/actions/workflows/runner-canary.yml)
[![Live demo check](https://github.com/rbchy/RbcTcsWorld/actions/workflows/live-demo.yml/badge.svg)](https://github.com/rbchy/RbcTcsWorld/actions/workflows/live-demo.yml)
![Stage](https://img.shields.io/badge/stage-affiliate%20site%20first%20%C2%B7%20own%20shop%20built-f59e0b)

![Tests](https://img.shields.io/badge/automated%20tests-2039-brightgreen?style=for-the-badge)
[![Coverage](https://img.shields.io/badge/line%20coverage-97.5%25-brightgreen?style=for-the-badge)](https://rbchy.github.io/RbcTcsWorld/coverage/)
[![Mutation](https://img.shields.io/badge/mutation%20score-86.9%25-brightgreen?style=for-the-badge)](https://rbchy.github.io/RbcTcsWorld/mutation/)
![Gates](https://img.shields.io/badge/CI%20gates-19%20%2F%2019-22c55e?style=for-the-badge&logo=githubactions&logoColor=white)
![Requirements](https://img.shields.io/badge/requirements%20traced-163%20%2F%20163-0d9488?style=for-the-badge)

![Java 21](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![React](https://img.shields.io/badge/React-Vite-61DAFB?style=for-the-badge&logo=react&logoColor=black)
![Playwright](https://img.shields.io/badge/Playwright-1.65-2EAD33?style=for-the-badge&logo=playwright&logoColor=white)
![Appium](https://img.shields.io/badge/Appium-3-662D91?style=for-the-badge&logo=appium&logoColor=white)
[![Allure](https://img.shields.io/badge/Allure-live%20report-orange?style=for-the-badge)](https://rbchy.github.io/RbcTcsWorld/)

</div>

**RbcTcsWorld Shop is an online store on its way to selling for real.** Everyday goods, delivered to your door. It has:

- **Shopping:** catalog, cart, size and colour variants, a faceted filter panel with "Shop by category" tiles, flash deals with a countdown, "customers also bought", reviews and a wishlist.
- **Checkout and orders:** checkout with a delivery address, guest checkout and an address book, a saved-card wallet, returns with prepaid return labels, HTML order e-mails and invoice PDFs.
- **Accounts:** two-factor sign-in and self-service account deletion.
- **Store back office:** coupons, multi-warehouse stock, reports and an append-only audit trail.
- **Amazon partner products, in six languages.**

The code was built test-first in the [E-Commerce Suite](https://github.com/rbchy/RbcTcsWorld_ECommerceSuite) and keeps
its full history and its quality bar: every change goes through 19 CI gates. This repository takes it through the
steps a live store needs: safe card payments, real tax, real shipping, e-mail from its own domain, backups, legal pages
and a business behind it.

**The plan: affiliate first, own shop later.** The site goes live first as an Amazon affiliate site (`STORE_SALES=off`): hand-picked partner products with the owner's own short reviews, bought at Amazon, with nothing sold here. Everything the own shop needs is built and tested, and opens with `STORE_SALES=on`.

<img src="docs/images/storefront.png" alt="The RbcTcsWorld Shop storefront: indigo header with the bag logo, side menu, the welcome offer WELCOME10 beside a picture of a delivery van on its way to a home, and Shop by category tiles" width="100%">

---

## 📌 At a Glance

<table>
<tr>
<td width="50%" valign="top">

**🙋 What this is**

A real online store that is also a complete **test system**. Every change reaches `main` only through a pull request with all CI gates green, and new behaviour arrives with its tests and a traced requirement. The suites:

- **Backend and API:** unit and integration tests, API, contract and database tests
- **Behaviour and UI:** Cucumber BDD, Selenium in three browsers, Playwright in five browsers and devices, Appium on an Android emulator and an iOS simulator
- **Non-functional:** accessibility, performance, OWASP ZAP and dependency scans, mutation testing

On top of that, a production stack with an encrypted backup and a restore drill is rebuilt in CI on every push.

</td>
<td width="50%" valign="top">

**📍 Quick facts**

| | |
|---|---|
| 🧪 **Automated tests** | 2,039 (2,034 passed, 5 skipped by design) |
| 🚦 **CI quality gates** | 19 / 19 on every push |
| 📈 **Coverage / mutation** | 97.5 % lines · 92.6 % branches · 86.9 % mutants killed |
| 🧭 **Requirements** | 163 / 163 traced to tests |
| 🐞 **Defects found by tests** | 31, each with root cause and regression test |
| 📜 **Decision records** | 26 ADRs |
| 🌐 **Browsers / devices** | Chrome · Firefox · Edge · WebKit · Pixel 7 · iPhone 14 · Android 14 · iOS |
| 🏁 **Stage** | affiliate site first · phases 1, 2, 3, 5 done |

</td>
</tr>
</table>

---

## 🚦 Project Status Board

<img src="docs/images/project-status.svg" alt="Project status board: 2,039 automated tests (2,034 passed, 5 skipped by design), 19 of 19 CI gates passed, 97.5 % line coverage, 86.9 % mutation score, 163 of 163 requirements traced; road to launch: first the Amazon affiliate site (in progress), phases 1, 2, 3 and 5 done, phases 0, 4 and 6 planned for the own shop" width="100%">

[![CI](https://github.com/rbchy/RbcTcsWorld/actions/workflows/ci.yml/badge.svg)](https://github.com/rbchy/RbcTcsWorld/actions/workflows/ci.yml)
![Last commit](https://img.shields.io/github/last-commit/rbchy/RbcTcsWorld?label=last%20commit)
![Open issues](https://img.shields.io/github/issues/rbchy/RbcTcsWorld?label=open%20issues)
![Open PRs](https://img.shields.io/github/issues-pr/rbchy/RbcTcsWorld?label=open%20PRs)

The board shows the GitHub Actions run of 10 Nov 2026 (commit `08577e1`, PR #36). The CI badge above is live;
the board is redrawn after a release with [`docs/images/make_readme_cards.py`](docs/images/make_readme_cards.py).

### 🆕 10 Nov 2026: the runner canary is green again, and a hidden flaky test is fixed

| | Before | Now |
|---|---|---|
| **Runner canary** (whole pipeline on the next GitHub image, ubuntu-26.04) | red every Monday since 26 Oct: Playwright 1.56.1 refuses the new image (*"Playwright does not support chromium on ubuntu26.04-x64"*); all 13 other jobs passed there | Playwright **1.65.0**: all 14 jobs green on ubuntu-26.04 *and* on the pinned ubuntu-24.04 |
| **"1 flaky" in green runs** | 10 of the last 11 `main` runs: the photo-upload test got *"Another photo is being processed"* (HTTP 429) on its first try | **[DEF-031](docs/qa/DEFECT_REPORTS.md#def-031):** the admin form now honours `Retry-After` (up to 4 tries) instead of showing the busy answer; 2 regression tests |

```mermaid
xychart-beta
    title "Runner canary on ubuntu-26.04: jobs passed (of 14)"
    x-axis ["26 Oct", "2 Nov", "9 Nov", "10 Nov (PW 1.65)"]
    y-axis "Jobs passed" 0 --> 14
    bar [13, 13, 13, 14]
```

## 🥧 Breakdown

```mermaid
pie showData
    title 2,039 automated tests by level
    "Backend unit + integration" : 1164
    "API, DB, BDD, Selenium UI" : 464
    "Playwright (5 browsers/devices)" : 403
    "Appium (Android + iOS)" : 8
```

```mermaid
pie showData
    title 163 traced requirements by area
    "Orders and inventory" : 16
    "Identity and access" : 17
    "Non-functional" : 20
    "Catalog" : 20
    "Storefront UI" : 14
    "Pricing, coupons, payments" : 18
    "Reviews and wishlist" : 11
    "Store admin back office" : 8
    "Fulfilment and returns" : 10
    "Customer e-mails" : 8
    "Legal and policies" : 4
    "Audit trail" : 4
    "Cart" : 3
    "Address book" : 3
    "Customer service" : 4
    "Search engines" : 3
```

```mermaid
pie showData
    title 31 defects found by the tests, by where they were
    "Product (backend, storefront, back office)" : 16
    "Test code" : 8
    "Test infrastructure / CI" : 7
```

```mermaid
xychart-beta
    title "Tests per CI job (one push)"
    x-axis ["Backend", "API+DB+BDD+UI", "Playwright", "Docker smoke", "Firefox UI", "Edge UI", "Android", "iOS"]
    y-axis "Tests" 0 --> 1200
    bar [1164, 464, 403, 63, 59, 59, 4, 4]
```

Every requirement links to its tests in the [traceability matrix](docs/qa/TRACEABILITY_MATRIX.md); CI fails if one has none.

## 📈 GitHub Stats

<img src="docs/images/github-stats.svg" alt="GitHub stats: 329 commits on 32 active days from 4 October to 10 November 2026 (history kept from the E-Commerce Suite), 36 merged pull requests in this repository, 0 open, 206 CI runs, about 57k lines of code; Java 75.5 %, JavaScript 14.5 %, TypeScript 4.6 %, CSS 2.1 %, other 3.3 %" width="100%">

![Commits](https://img.shields.io/github/commit-activity/t/rbchy/RbcTcsWorld?label=commits)
![Commit activity](https://img.shields.io/github/commit-activity/m/rbchy/RbcTcsWorld?label=commits%20this%20month)
![Merged PRs](https://img.shields.io/github/issues-pr-closed/rbchy/RbcTcsWorld?label=closed%20PRs)
![Top language](https://img.shields.io/github/languages/top/rbchy/RbcTcsWorld)
![Code size](https://img.shields.io/github/languages/code-size/rbchy/RbcTcsWorld)
![Stars](https://img.shields.io/github/stars/rbchy/RbcTcsWorld?style=social)

The badges update live from GitHub; the card above is a snapshot of the same day as the status board.

---

## 🏗️ Architecture

### System context

```mermaid
flowchart LR
    C([👤 Customer<br/>browser · phone]) --> SF
    A([🧑‍💼 Store owner<br/>admin back office]) --> SF
    subgraph Shop["RbcTcsWorld Shop"]
        SF[React storefront<br/>Vite · 6 languages · PWA]
        BE[Spring Boot 4.1 API<br/>Java 21 · JWT]
        DB[(PostgreSQL 16<br/>Flyway, 35 migrations)]
        RD[(Redis)]
        SF -->|/api| BE
        BE --> DB
        BE --> RD
    end
    SF -.->|card goes straight to Stripe| STE[Stripe Elements]
    BE -->|PaymentIntent · Tax| ST[Stripe + Stripe Tax]
    ST -->|signed webhook| BE
    BE -->|labels · tracking · returns| SH[Shippo]
    BE -->|order e-mail from own domain| SES[Amazon SES]
    SF -->|Buy at Amazon + disclosure| AMZ[Amazon partner links]
```

### Backend modules (one package per business capability)

```mermaid
flowchart TB
    subgraph Customer["Customer side"]
        product[product · catalog, variants, photos]
        cart[cart]
        checkout[checkout]
        pricing[pricing · flash deals]
        coupon[coupon]
        order[order · state machine]
        payment[payment · Stripe Elements]
        tax[tax · Stripe Tax]
        fulfillment[fulfillment · Shippo]
        returns[returns · prepaid labels]
        review[review]
        wishlist[wishlist]
        address[address book]
        account[account · deletion]
    end
    subgraph Platform["Platform"]
        auth[auth · JWT, 2FA, roles]
        notification[notification · SES, HTML mail]
        audit[audit · hash-chained trail]
        seo[seo · sitemap, product data]
        legal[legal · policy versions]
        contact[contact · tickets]
    end
    subgraph Back["Back office"]
        admin[admin]
        inventory[inventory · warehouses]
        report[report · CSV]
        store[store · STORE_SALES on/off]
    end
    cart --> checkout --> order --> payment
    checkout --> pricing & coupon & tax
    order --> fulfillment --> returns
    order --> notification
    order --> inventory
    admin --> audit
    auth -.guards.-> Customer & Back
```

### Card payment: the order is paid only on Stripe's word ([ADR-0011](docs/adr/0011-stripe-elements-and-webhooks.md))

```mermaid
sequenceDiagram
    autonumber
    actor C as Customer
    participant SF as Storefront
    participant BE as Shop API
    participant S as Stripe
    participant DB as PostgreSQL
    C->>SF: Place order
    SF->>BE: POST /api/orders (status PLACED)
    SF->>BE: POST /api/orders/{id}/card-payment
    BE->>S: create PaymentIntent (idempotency key order-{id}-elements-{cents})
    S-->>BE: client secret
    BE-->>SF: client secret only
    C->>S: card number in Stripe's iframe (never reaches the shop)
    S-->>SF: payment succeeded
    par confirm call
        SF->>BE: POST .../card-payment/confirm
        BE->>S: read the PaymentIntent back
    and signed webhook
        S->>BE: POST /api/payments/stripe/webhook (HMAC, ≤ 5 min)
        BE->>DB: remember event id (stripe_events)
    end
    BE->>DB: UPDATE payment_intents SET settled_at … WHERE settled_at IS NULL
    Note over BE,DB: whichever comes first pays the order, exactly once<br/>and a wrong amount never pays
    BE->>DB: order PLACED → PAID + timeline event
    BE-->>C: HTML confirmation e-mail (Amazon SES)
```

### Order lifecycle (`OrderStatus.ALLOWED`; every other move is 409)

```mermaid
stateDiagram-v2
    [*] --> PLACED
    PLACED --> PAID: pay (Stripe's word)
    PLACED --> CANCELLED: cancel
    PAID --> SHIPPED: ship (Shippo label)
    PAID --> CANCELLED: cancel + refund
    SHIPPED --> DELIVERED: carrier says delivered
    DELIVERED --> RETURN_REQUESTED: request return
    RETURN_REQUESTED --> DELIVERED: reject return
    RETURN_REQUESTED --> RETURNED: approve + refund
    RETURNED --> [*]
    CANCELLED --> [*]
```

### Core data model (from the Flyway migrations)

```mermaid
erDiagram
    USERS ||--o{ ORDERS : places
    USERS ||--o{ CART_ITEMS : fills
    USERS ||--o{ CUSTOMER_ADDRESSES : keeps
    USERS ||--o{ PAYMENT_METHODS : saves
    USERS ||--o{ REVIEWS : writes
    USERS ||--o{ WISHLIST_ITEMS : wishes
    PRODUCTS ||--o{ CART_ITEMS : "in"
    PRODUCTS ||--o{ ORDER_ITEMS : "sold as"
    PRODUCTS ||--o{ WAREHOUSE_STOCK : "stocked as"
    PRODUCTS ||--o{ REVIEWS : "reviewed in"
    WAREHOUSES ||--o{ WAREHOUSE_STOCK : holds
    ORDERS ||--|{ ORDER_ITEMS : contains
    ORDERS ||--o{ ORDER_EVENTS : "timeline"
    ORDERS ||--o{ PAYMENT_INTENTS : "paid by"
    ORDERS ||--o{ ORDER_ALLOCATIONS : "allocated from"
    ORDERS ||--o{ RETURN_REQUESTS : "returned via"
    ORDERS ||--o{ COUPON_REDEMPTIONS : "discounted by"
    COUPONS ||--o{ COUPON_REDEMPTIONS : "redeemed as"
    ORDERS {
        bigint id PK
        varchar status "PLACED…RETURNED, DB CHECK"
        numeric total
        varchar policy_version
    }
    PAYMENT_INTENTS {
        varchar stripe_id
        timestamp settled_at "claimed once"
    }
    PRODUCTS {
        bigint id PK
        varchar sku UK
        numeric price
        varchar affiliate_url "partner product"
    }
```

<sub>29 tables in all; also `stripe_events`, `stock_movements`, `notifications`, `email_suppressions`, `audit_log` + `audit_head` (hash chain), `login_challenges`, `refresh_tokens`, `product_pictures`, `contact_messages`.</sub>

### Production stack ([ADR-0016](docs/adr/0016-production-stack-backups-and-alerts.md)), rebuilt and restore-tested in CI on every push

```mermaid
flowchart LR
    NET((Internet<br/>80 / 443 only)) --> CADDY[Caddy<br/>HTTPS, auto certificates]
    CADDY --> FE[frontend<br/>nginx + React build]
    CADDY --> BE[backend<br/>Spring Boot, non-root]
    BE --> PG[(PostgreSQL 16)]
    BE --> RD[(Redis)]
    CRON[[cron 02:15]] --> BK[backup.sh<br/>pg_dump → AES-256 encrypted]
    BK --> PG
    BK --> S3[(S3-compatible bucket<br/>off the server)]
    S3 --> DRILL[restore-drill.sh<br/>restore into a scratch DB + checks]
    HC[[healthcheck.sh]] --> ALERT[alert.sh<br/>e-mail on failure]
```

### The 19 gates as a pipeline

```mermaid
flowchart TD
    P([push · pull request]) --> BT[Backend unit + integration<br/>coverage · traceability<br/>gates 1, 3, 13]
    P --> MT[Mutation testing PIT<br/>gate 4]
    P --> JL[Jenkinsfile lint<br/>gate 14]
    BT --> API[API + DB + BDD + Selenium<br/>contracts · axe-core<br/>gates 2, 5, 7]
    BT --> XB[UI in Firefox + Edge<br/>gate 8]
    BT --> PW[Playwright: Chromium, Firefox,<br/>WebKit, Pixel 7, iPhone 14 · gate 16]
    BT --> AN[Appium Android<br/>gate 17]
    BT --> IOS[Appium iOS, macOS runner<br/>gate 18]
    BT --> DK[Whole app in Docker<br/>gate 12]
    BT --> DEMO[Public demo stack<br/>gate 15]
    BT --> PROD[Production stack + restore drill<br/>gate 19]
    BT --> SEC[ZAP + OSV on SBOMs<br/>+ openapi-diff<br/>gates 6, 10, 11]
    API & MT & PW --> PUB[(Allure, Cucumber, JaCoCo, PIT,<br/>Playwright reports on Pages)]
    PERF[k6 / JMeter, manual workflow<br/>gate 9] -.-> PUB
    CAN[[Runner canary, Mondays:<br/>the same pipeline on the next image]] -.-> P
```

### What the shop can do

```mermaid
mindmap
  root((RbcTcsWorld<br/>Shop))
    Shopping
      catalog, variants, filters
      flash deals, also bought
      reviews, wishlist
      6 languages, PWA
    Checkout
      guest or account
      address book
      Stripe Elements, wallet
      Stripe Tax, coupons
    After the order
      HTML e-mail, invoice PDF
      Shippo labels, tracking
      prepaid return labels
    Back office
      products + photo upload
      multi-warehouse stock
      coupons, reports CSV
      hash-chained audit trail
      customer messages
    Trust
      2FA, account deletion
      policy version per order
      backups + restore drill
      Amazon partner disclosure
```

---

## Where it stands

| | |
|---|---|
| Independent since | 25 Oct 2026 (`c5c5091`); the full git history of the code is kept |
| Done in this repository | Phase 1: own project names and links, roadmap, [ADR-0010](docs/adr/0010-from-portfolio-to-live-store.md); a store look (indigo frame, bag logo, Plus Jakarta Sans), the welcome banner with the delivery picture, "Shop by category" tiles |
| Plan | **Affiliate first, own shop later:** the site goes live as an Amazon affiliate site with `STORE_SALES=off` (nothing can be bought here, Amazon's cookie statement in the privacy policy, [ADR-0026](docs/adr/0026-partner-only-site.md)); the own shop opens later with `STORE_SALES=on` |
| Quality bar | 2,039 automated tests, 19 CI quality gates, 97.5 % line coverage, 86.9 % mutation score, 163 traced requirements |
| Phase 2 (done) | Stripe Elements and signed webhooks: card numbers never reach our server, an order is paid only on Stripe's word, exactly once ([ADR-0011](docs/adr/0011-stripe-elements-and-webhooks.md)) |
| Phase 3 (done) | Stripe Tax: the right sales tax for every delivery address, recorded with Stripe after payment ([ADR-0012](docs/adr/0012-stripe-tax.md)); Shippo labels with real tracking numbers, delivered when the carrier says so ([ADR-0013](docs/adr/0013-shippo-labels-and-tracking.md)); order e-mail from the store's own domain through Amazon SES, bounced addresses never e-mailed again ([ADR-0014](docs/adr/0014-amazon-ses-mail.md)) |
| Product photos (done) | The owner uploads a photo from the admin form (phone or computer); the backend turns it upright, scales it to 1600 px and saves a new file without EXIF or GPS data, in the database so the backups cover it ([ADR-0017](docs/adr/0017-product-photo-uploads.md)) |
| Customer service (done) | A contact form on every page (ticket number, promised answer time, spam-proof without a CAPTCHA) and Admin -> Messages to answer by e-mail ([ADR-0018](docs/adr/0018-contact-form.md)) |
| Search engines (done) | Every product at a real address (`/products/12/travel-mug`) with title, description, price and stock data for Google; robots.txt and a sitemap; private pages and the demo never indexed ([ADR-0019](docs/adr/0019-search-engines.md)) |
| Partner products (done) | Amazon and other affiliate links next to the shop's own products: "Buy at Amazon" instead of price and cart, the required disclosure, never in our cart or orders ([ADR-0020](docs/adr/0020-affiliate-products.md)); `STORE_SALES=off` runs the whole site as an affiliate site first, with nothing to buy here ([ADR-0026](docs/adr/0026-partner-only-site.md)) |
| Since then (done) | Owner's own product descriptions (ADR-0021); blocked e-mail addresses in the admin; tax reversal on refunds and product tax codes (ADR-0012); product weight and box size for labels (ADR-0013); self-service account deletion (ADR-0022); prepaid return labels (ADR-0023); the policy version on every order (ADR-0024); HTML e-mail (ADR-0025) |
| Phone app (planned, phase 7) | First an installable website (PWA, also on iPhone), then an Android app (Trusted Web Activity) from GitHub Actions, the path already proven on the [portfolio site](https://rbchy.github.io/rbchy/); Amazon links inside an app need Amazon's separate approval, so the store app follows the own shop ([roadmap](docs/store/ROADMAP.md)) |
| Not decided yet | what the own shop will sell, where the products come from (phase 0, owner, after the affiliate start) |

### From portfolio code to a live store

```mermaid
timeline
    title RbcTcsWorld Shop, 2026
    4 Oct : the code starts in the E-Commerce Suite, test-first
    25 Oct : own repository, store look · ADR-0010
    26 Oct : phase 2 · Stripe Elements + signed webhooks
    27 Oct : phase 3 · Stripe Tax, Shippo, Amazon SES
    28 Oct : phase 5 · production stack, encrypted backups, restore drill · partner products
    3 Nov : affiliate first · STORE_SALES=off · ADR-0026
    10 Nov : Playwright 1.65 · runner canary green · DEF-031
    Next : affiliate site live (owner - server, domain, 10-15 products)
    Later : phases 0, 4, 6 · the own shop opens with STORE_SALES=on
```

## Roadmap to launch

The details, owners and running costs are in [docs/store/ROADMAP.md](docs/store/ROADMAP.md).

| Phase | What | Who | Status |
|---|---|---|---|
| First | Amazon affiliate site: server and domain, `STORE_SALES=off`, 10-15 partner products with own reviews, Associates application | Owner (engineering done) | **in progress** |
| 0 | Decide the own shop's products, suppliers and brand | Owner | after the affiliate start |
| 1 | This repository: history, links, project names, README, roadmap, decision record, store look | Engineering | **done** |
| 2 | Card payments through Stripe Elements and webhooks (card numbers never reach our server) | Engineering | **done** |
| 3 | Real sales tax, shipping rates and labels, e-mail from the store's domain | Engineering | **done** (accounts and domain: owner) |
| 4 | Business registration, sales tax license, privacy policy, terms, return policy | Owner + professionals | policy pages built; LLC, EIN, license: owner, for the own shop |
| 5 | Production server: demo mode off, daily backups, monitoring, restore drill | Engineering | **built** (server, domain, bucket: owner) |
| 6 | User acceptance test with real cards and refunds, then the own shop opens (`STORE_SALES=on`) | Owner + Engineering | planned |

```mermaid
flowchart LR
    A[Affiliate site<br/>in progress]:::now --> P0[0 · Products<br/>owner]:::later
    P1[1 · Foundation]:::done --> P2[2 · Card payments]:::done --> P3[3 · Tax, shipping, mail]:::done --> P5[5 · Production server]:::done
    P5 --> A
    P0 --> P4[4 · Business + legal<br/>owner + professionals]:::later --> P6[6 · UAT with real cards]:::later --> OPEN([Own shop opens<br/>STORE_SALES=on]):::later
    P6 -.-> P7[7 · Phone app<br/>PWA, then Android]:::later
    classDef done fill:#dcfce7,stroke:#16a34a,color:#14532d
    classDef now fill:#fef3c7,stroke:#d97706,color:#78350f
    classDef later fill:#e0e7ff,stroke:#6366f1,color:#312e81
```

Every phase keeps the inherited rule: a change reaches `main` only through a pull request with all CI gates green, and
new behaviour arrives with its tests and a traced requirement.

---

## Reviewing this in 5 minutes

1. **Where the store is going:** [Roadmap](docs/store/ROADMAP.md) and the decision to build it on this code,
   [ADR-0010](docs/adr/0010-from-portfolio-to-live-store.md).
2. **Latest build:** the [GitHub Actions runs](https://github.com/rbchy/RbcTcsWorld/actions) (19 gates per push).
3. **Live reports:** [all reports](https://rbchy.github.io/RbcTcsWorld/reports/) · [Allure](https://rbchy.github.io/RbcTcsWorld/) ·
   [Cucumber](https://rbchy.github.io/RbcTcsWorld/cucumber/) · [JaCoCo coverage](https://rbchy.github.io/RbcTcsWorld/coverage/) ·
   [PIT mutation](https://rbchy.github.io/RbcTcsWorld/mutation/) · [Playwright](https://rbchy.github.io/RbcTcsWorld/playwright/)
4. **Defects the tests actually found:** [Defect Reports](docs/qa/DEFECT_REPORTS.md) - 31 entries, each with
   evidence, root cause, fix and regression test.
5. **How release decisions are made:** [Test Strategy](docs/qa/TEST_STRATEGY.md) · [Risk Register](docs/qa/RISK_REGISTER.md) ·
   [Test Summary Report (GO / NO-GO)](docs/qa/TEST_SUMMARY_REPORT.md) · [UAT Plan](docs/qa/UAT_PLAN.md)
   (10 tasks for real users, phase 6 runs it with small real orders)
6. **Why it is built this way:** [Architecture Decision Records](docs/adr/) - 26 short records, each with the
   alternatives and the price paid. Commands: `make help` · [Contributing](CONTRIBUTING.md) · [MIT License](LICENSE)

---

## Quality gates

A gate fails the build. Nothing is "reported only" unless the table says so.

| # | Gate | Threshold | Current | Tool |
|---|---|---|---|---|
| 1 | Backend unit + integration | 100 % pass | 1,164 / 1,164 | JUnit 5, Mockito, H2 + Flyway |
| 2 | API, database, BDD, UI | 100 % pass | 464 / 464 | REST Assured, JDBC, Cucumber 7, Selenium 4, Mailpit |
| 3 | Line / branch coverage | >= 97 % / >= 88 % | 97.5 % / 92.6 % | JaCoCo |
| 4 | Mutation score | >= 84 % | 86.9 % (test strength 93.8 %) | PIT |
| 5 | API contract, consumer side | every response matches its schema | 12 strict JSON Schemas | json-schema-validator |
| 6 | API contract, provider side | no breaking change vs. approved baseline | 0 (gate has its own self-test) | openapi-diff |
| 7 | Accessibility | 0 serious / critical | 0 on 23 pages | axe-core, WCAG 2.1 A + AA |
| 8 | Cross-browser | 100 % pass | 59 / 59 in Firefox and Edge | Selenium, CI matrix |
| 9 | Performance | SLO thresholds (p95, error rate, no overselling) | within SLO | k6, JMeter (manual workflow) |
| 10 | DAST | 0 Medium / High | 0 | OWASP ZAP API scan |
| 11 | Dependencies | 0 known vulnerabilities, 0 open exceptions | 0 (complete SBOMs, self-test passed) | OSV-Scanner on CycloneDX SBOMs |
| 12 | Containers | stack healthy, smoke tests pass | 63 / 63 | Docker Compose |
| 13 | Traceability | every requirement has a test | 163 / 163 | `docs/qa/check_rtm.py` |
| 14 | Pipeline definition | Jenkinsfile valid | validated by a real Jenkins | declarative linter |
| 15 | Public demo stack | HTTPS, only 80/443 open, demo safeguards, nightly reset | all smoke checks pass | Docker Compose, Caddy, `deploy/smoke.sh` |
| 16 | Playwright (TypeScript) | 100 % pass, visual baselines match | 398 / 398 + 5 skipped by design (Chromium, Firefox, WebKit, Pixel 7, iPhone 14), 0 flaky | Playwright, axe-core |
| 17 | Phone: Android | 100 % pass (one rerun, reported as flaky) | 4 / 4 in Chrome on Android 14 | Appium 3 + UiAutomator2, Android emulator |
| 18 | Phone: iOS | 100 % pass (one rerun, reported as flaky) | 4 / 4 in Safari on the newest iPhone simulator | Appium 3 + XCUITest, macOS runner |
| 19 | Production stack | live settings, only 80/443 open, encrypted off-site backup, restore drill, restore, health alerts | all steps pass | Docker Compose, moto (S3 API), shell + cron |

Thresholds are set just under the measured value on purpose: they stop regressions without inviting
tests that exist only to move a number. How the test layers fit together and what each one found is described in
[docs/FOUNDATION.md](docs/FOUNDATION.md).

---

## Before the first real order

These are the gaps between the demo and a live store, in the order the roadmap closes them. Stating them is part of
the plan, not a footnote.

- **For the affiliate site (first):** only the owner's part is left: a server and domain, 10-15 Amazon products with
  his own words and photos, the Associates application. `STORE_SALES=off` needs none of the services below.

- **The live services are built but not switched on.** Stripe Elements (phase 2), Stripe Tax, Shippo and Amazon SES
  (phase 3) work in test mode; each needs the owner's account, keys and, for e-mail, the store's own domain. Until
  then the store runs on test cards, a flat 6 % tax, made-up tracking numbers and Mailpit.
- **Only the demo server is online.** The production stack (backups, restore drill, alerts) is built and proven in CI; it needs its own server and domain (phase 5).
- **No business yet:** the policy pages exist as drafts with placeholders; LLC, EIN, sales tax license and a lawyer's review come next (phase 4).
- **Products are samples** with drawn pictures until the owner decides what to sell (phase 0); photo upload is ready for the real ones.

---

## Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4.1 (Spring 7, Jackson 3, Tomcat 11), Spring Security + JWT, PostgreSQL 16, Flyway |
| Storefront | React + Vite, six languages, installable as a desktop app (PWA) |
| Test automation | JUnit 5, REST Assured, Selenium 4 (Page Objects), Cucumber 7, Playwright (TypeScript), Appium 3, axe-core, JDBC |
| Test quality | JaCoCo, PIT |
| Non-functional | k6, OWASP ZAP, OSV-Scanner, openapi-diff |
| Reporting | Allure (trend history), Cucumber HTML, Playwright HTML, per-build QA dashboard |
| Delivery | Docker (multi-stage, non-root), Docker Compose, GitHub Actions, Jenkins |

---

## Run it

**Whole store, one command** (PostgreSQL + Redis + Mailpit + backend + storefront):

```bash
make doctor     # checks the machine first
make app        # storefront http://localhost:5173, API http://localhost:8081
make test       # the automated suites
make help       # everything else
```

Or double-click the launcher in `desktop/` (Mac `.command`, Windows `.bat`, Linux `.sh`).

**Development** (backend from Eclipse or Maven, live-reloading storefront):

```bash
docker compose up -d                              # PostgreSQL, Redis, Mailpit
cd backend && mvn spring-boot:run                 # API on 8081; Flyway creates tables and demo data
cd frontend && npm install && npm run dev         # storefront on 5173
```

| | |
|---|---|
| Backend API | http://localhost:8081 · OpenAPI description at `/v3/api-docs` |
| E-mails | Mailpit, http://localhost:8025 |
| Admin (dev only) | `admin@rbctcsworld.com` / `Admin@12345`, override with `ADMIN_EMAIL`, `ADMIN_PASSWORD` |
| Coupons | `WELCOME10`, `SAVE5`, `EXPIRED20`, `FUTURE15`, `DISABLED` |
| Test cards | `4242 4242 4242 4242` approved · `4000 0000 0000 0002` declined · any future expiry, any CVV |

**Eclipse:** File -> Import -> Maven -> Existing Maven Projects -> select this folder. The projects are
`rbctcsworld-shop`, `rbctcsworld-shop-backend` and `rbctcsworld-shop-automation`. After a pull: right-click the
project -> Maven -> Update Project.

## Run the tests

The backend tests need nothing running. **Every other suite tests a running shop: start it first** (Docker Desktop
running), otherwise each test fails with "Connection refused"; both suites then stop with one plain message instead.

```bash
git pull                                                               # the newest code and tests
mvn -f backend/pom.xml verify                                          # unit + integration + coverage gate (no shop needed)

make app                                                               # start the shop: 5173 + 8081 (once per session)
mvn -f automation/pom.xml clean test -DexcludedGroups=ui,mobile        # API, contract, DB, BDD, security
mvn -f automation/pom.xml clean test -Dgroups=ui -Dbrowser=edge        # UI: chrome | firefox | edge | safari
make playwright                                                        # Playwright: installs its browsers, 5 browsers and devices
make down                                                              # stop the shop (the data is kept)
```

---

## Documentation

**Store:** [Roadmap](docs/store/ROADMAP.md) · [ADR-0010: from portfolio to live store](docs/adr/0010-from-portfolio-to-live-store.md) ·
[ADR-0011: Stripe Elements and webhooks](docs/adr/0011-stripe-elements-and-webhooks.md) · [Stripe test-mode guide (Bangla)](docs/modules/STRIPE_ELEMENTS_BN.md) ·
[ADR-0012: Stripe Tax](docs/adr/0012-stripe-tax.md) · [Stripe Tax guide (Bangla)](docs/modules/STRIPE_TAX_BN.md) ·
[ADR-0013: Shippo](docs/adr/0013-shippo-labels-and-tracking.md) · [Shippo guide (Bangla)](docs/modules/SHIPPO_SHIPPING_BN.md) ·
[ADR-0014: Amazon SES](docs/adr/0014-amazon-ses-mail.md) · [SES guide (Bangla)](docs/modules/AMAZON_SES_MAIL_BN.md) ·
[ADR-0015: policies](docs/adr/0015-policies-from-the-shops-settings.md) ·
[ADR-0016: production stack](docs/adr/0016-production-stack-backups-and-alerts.md) · [Production server guide (Bangla)](docs/deploy/PRODUCTION_BN.md) ·
[ADR-0017: product photos](docs/adr/0017-product-photo-uploads.md) · [Product photo guide (Bangla)](docs/modules/PRODUCT_PHOTOS_BN.md) ·
[ADR-0018: contact form](docs/adr/0018-contact-form.md) · [Contact form guide (Bangla)](docs/modules/CONTACT_FORM_BN.md) ·
[ADR-0019: search engines](docs/adr/0019-search-engines.md) · [Search engine guide (Bangla)](docs/modules/SEARCH_ENGINES_BN.md) ·
[ADR-0020: partner products](docs/adr/0020-affiliate-products.md) · [Amazon affiliate guide (Bangla)](docs/modules/AFFILIATE_PRODUCTS_BN.md) ·
[ADR-0021: product descriptions](docs/adr/0021-product-descriptions.md) · [ADR-0022: account deletion](docs/adr/0022-self-service-account-deletion.md) · [ADR-0023: return labels](docs/adr/0023-prepaid-return-labels.md) · [ADR-0024: policy version per order](docs/adr/0024-policy-version-per-order.md) · [ADR-0025: HTML e-mail](docs/adr/0025-html-email-twin.md) · [ADR-0026: affiliate-only site](docs/adr/0026-partner-only-site.md) · [Affiliate guide, step by step (Bangla)](docs/modules/AFFILIATE_PRODUCTS_BN.md) · [Phase 4: business and legal (Bangla)](docs/modules/PHASE4_BUSINESS_LEGAL_BN.md) ·
[The inherited foundation](docs/FOUNDATION.md)

**QA documents (English):** [Strategy](docs/qa/TEST_STRATEGY.md) · [Plan](docs/qa/TEST_PLAN.md) ·
[Risks](docs/qa/RISK_REGISTER.md) · [Traceability (163 requirements)](docs/qa/TRACEABILITY_MATRIX.md) ·
[Defects (31)](docs/qa/DEFECT_REPORTS.md) · [Test Summary Report](docs/qa/TEST_SUMMARY_REPORT.md) ·
[UAT Plan](docs/qa/UAT_PLAN.md) · [UAT Summary](docs/qa/UAT_SUMMARY.md)

**Guides (Bangla):** [HELP_BN](docs/HELP_BN.md) to start, and one guide per feature or test layer in
[docs/modules/](docs/modules/) (cart, orders, payments, returns, admin back office, audit trail, Playwright, Appium,
security, mutation testing, CI/CD and more).

## 🔗 Related Projects

| Repository | What it is |
|---|---|
| [RbcTcsWorld_ECommerceSuite](https://github.com/rbchy/RbcTcsWorld_ECommerceSuite) | Where this code was built test-first: 1,489 tests, 18 CI gates, 29 defect reports |
| [ranajitchowdhury](https://github.com/rbchy/ranajitchowdhury) | Portfolio: all 37 repositories with their live CI results |
| [rbchy](https://github.com/rbchy/rbchy) | Profile, website and installable app (the PWA path the store app will follow in phase 7) |
| [Amazon-Cypress-JavaScript-Automation](https://github.com/rbchy/Amazon-Cypress-JavaScript-Automation) | Cypress BDD on Amazon-style shopping flows: 39 scenarios on 3 browsers |
| [AmazonSeleniumTest](https://github.com/rbchy/AmazonSeleniumTest) | Selenium + Cucumber against live Amazon, reporting bot walls instead of bypassing them |

---

## 👤 Owner

<div align="center">

**Ranajit Baran Chowdhury (RB Chowdhury)**
QA Automation Engineer / SDET · West Norriton, Pennsylvania, USA

[![LinkedIn](https://img.shields.io/badge/LinkedIn-0077B5?style=for-the-badge&logo=linkedin&logoColor=white)](https://www.linkedin.com/in/rbchy/)
[![GitHub](https://img.shields.io/badge/GitHub-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/rbchy)
[![Website](https://img.shields.io/badge/Website-rbchy.github.io%2Frbchy-0d9488?style=for-the-badge&logo=githubpages&logoColor=white)](https://rbchy.github.io/rbchy/)
[![Portfolio](https://img.shields.io/badge/Portfolio-Website-blueviolet?style=for-the-badge&logo=wix&logoColor=white)](https://rbc6543.wixsite.com/rbc-portfolio)
[![Gmail](https://img.shields.io/badge/Gmail-D14836?style=for-the-badge&logo=gmail&logoColor=white)](mailto:chyranajit@gmail.com)

</div>

## License

[MIT](LICENSE)

---

A store is ready when its customers can trust it, not when its code compiles. This one starts from tests that
already find the expensive defects, and adds what real customers need, one tested step at a time.
