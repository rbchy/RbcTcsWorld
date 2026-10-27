# RbcTcsWorld Shop

[![CI](https://github.com/rbchy/RbcTcsWorld/actions/workflows/ci.yml/badge.svg)](https://github.com/rbchy/RbcTcsWorld/actions/workflows/ci.yml)
![Stage](https://img.shields.io/badge/stage-pre--launch%20%C2%B7%20phase%202%20next-f59e0b)
![Java 21](https://img.shields.io/badge/Java-21-blue)
![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-green)
![Tests](https://img.shields.io/badge/automated%20tests-1497-brightgreen)
[![Coverage](https://img.shields.io/badge/line%20coverage-97.3%25-brightgreen)](https://rbchy.github.io/RbcTcsWorld/coverage/)
[![Mutation](https://img.shields.io/badge/mutation%20score-89.3%25-brightgreen)](https://rbchy.github.io/RbcTcsWorld/mutation/)
[![Allure](https://img.shields.io/badge/Allure-live%20report-orange)](https://rbchy.github.io/RbcTcsWorld/)

**RbcTcsWorld Shop is an online store on its way to selling for real.** Everyday goods, delivered to your door:
catalog, cart, checkout with a delivery address, guest checkout and an address book, size and colour variants, a
faceted filter panel with "Shop by category" tiles, flash deals with a countdown, "customers also bought", reviews,
wishlist, returns, order e-mails, invoice PDFs, two-factor sign-in, a saved-card wallet, a store back office with
coupons, multi-warehouse stock, reports and an append-only audit trail, in six languages.

The code was built test-first in the [E-Commerce Suite](https://github.com/rbchy/RbcTcsWorld_ECommerceSuite) and keeps
its full history and its quality bar: every change goes through 18 CI gates. This repository takes it through the
steps a live store needs: safe card payments, real tax, real shipping, e-mail from its own domain, backups, legal pages
and a business behind it. **Nothing here takes real orders yet**; until launch the store runs in demo mode with test
cards only.

Owner: **RB Chowdhury**, QA Automation Engineer / SDET - [GitHub](https://github.com/rbchy) · [LinkedIn](https://www.linkedin.com/in/rbchy/) · [Website](https://rbchy.github.io/rbchy/)

<img src="docs/images/storefront.png" alt="The RbcTcsWorld Shop storefront: indigo header with the bag logo, side menu, the welcome offer WELCOME10 beside a picture of a delivery van on its way to a home, and Shop by category tiles" width="100%">

---

## 🚦 Project Status Board

<img src="docs/images/project-status.svg" alt="Project status board: 1,497 automated tests (1,494 passed, 3 skipped by design), 18 of 18 CI gates passed, 97.3 % line coverage, 89.3 % mutation score, 129 of 129 requirements traced; road to launch: phase 1 done, phase 0 in progress, phase 2 next" width="100%">

[![CI](https://github.com/rbchy/RbcTcsWorld/actions/workflows/ci.yml/badge.svg)](https://github.com/rbchy/RbcTcsWorld/actions/workflows/ci.yml)
![Last commit](https://img.shields.io/github/last-commit/rbchy/RbcTcsWorld?label=last%20commit)
![Open issues](https://img.shields.io/github/issues/rbchy/RbcTcsWorld?label=open%20issues)
![Open PRs](https://img.shields.io/github/issues-pr/rbchy/RbcTcsWorld?label=open%20PRs)

The board shows the GitHub Actions run of `main` on 26 Oct 2026 (commit `ddcd72c`). The CI badge above is live;
the board is redrawn after a release with [`docs/images/make_readme_cards.py`](docs/images/make_readme_cards.py).

## 🥧 Breakdown

```mermaid
pie showData
    title 1,497 automated tests by level
    "Backend unit + integration" : 772
    "API, DB, BDD, Selenium UI" : 444
    "Playwright (5 browsers/devices)" : 273
    "Appium (Android + iOS)" : 8
```

```mermaid
pie showData
    title 129 traced requirements by area
    "Orders and inventory" : 16
    "Identity and access" : 16
    "Non-functional" : 17
    "Catalog" : 14
    "Storefront UI" : 14
    "Pricing, coupons, payments" : 13
    "Reviews and wishlist" : 11
    "Store admin back office" : 8
    "Fulfilment and returns" : 6
    "Customer e-mails" : 4
    "Audit trail" : 4
    "Cart" : 3
    "Address book" : 3
```

Every requirement links to its tests in the [traceability matrix](docs/qa/TRACEABILITY_MATRIX.md); CI fails if one has none.

## 📈 GitHub Stats

<img src="docs/images/github-stats.svg" alt="GitHub stats: 239 commits on 21 active days from 4 to 26 October 2026 (history kept from the E-Commerce Suite), 6 merged pull requests in this repository, 0 open, 29 CI runs, about 51k lines of code; Java 73.9 %, JavaScript 16.0 %, TypeScript 4.0 %, CSS 2.7 %, other 3.4 %" width="100%">

![Commits](https://img.shields.io/github/commit-activity/t/rbchy/RbcTcsWorld?label=commits)
![Commit activity](https://img.shields.io/github/commit-activity/m/rbchy/RbcTcsWorld?label=commits%20this%20month)
![Merged PRs](https://img.shields.io/github/issues-pr-closed/rbchy/RbcTcsWorld?label=closed%20PRs)
![Top language](https://img.shields.io/github/languages/top/rbchy/RbcTcsWorld)
![Code size](https://img.shields.io/github/languages/code-size/rbchy/RbcTcsWorld)
![Stars](https://img.shields.io/github/stars/rbchy/RbcTcsWorld?style=social)

The badges update live from GitHub; the card above is a snapshot of the same day as the status board.

---

## Where it stands

| | |
|---|---|
| Independent since | 25 Oct 2026 (`c5c5091`); the full git history of the code is kept |
| Done in this repository | Phase 1: own project names and links, roadmap, [ADR-0010](docs/adr/0010-from-portfolio-to-live-store.md); a store look (indigo frame, bag logo, Plus Jakarta Sans), the welcome banner with the delivery picture, "Shop by category" tiles |
| Inherited quality bar | 1,497 automated tests, 18 CI quality gates, 97.3 % line coverage, 89.3 % mutation score, 129 traced requirements |
| Next | **Phase 2 - safe card payments**: Stripe Elements and webhooks, so card numbers never reach our server |
| Not decided yet | what the store sells, where the products come from (phase 0, owner) |

## Roadmap to launch

The details, owners and running costs are in [docs/store/ROADMAP.md](docs/store/ROADMAP.md).

| Phase | What | Who | Status |
|---|---|---|---|
| 0 | Decide the products, suppliers and brand | Owner | in progress |
| 1 | This repository: history, links, project names, README, roadmap, decision record, store look | Engineering | **done** |
| 2 | Card payments through Stripe Elements and webhooks (card numbers never reach our server) | Engineering | next |
| 3 | Real sales tax, shipping rates and labels, e-mail from the store's domain | Engineering | planned |
| 4 | Business registration, sales tax license, privacy policy, terms, return policy | Owner + professionals | planned |
| 5 | Production server: demo mode off, daily backups, monitoring, restore drill | Engineering | planned |
| 6 | User acceptance test with real cards and refunds, then launch | Owner + Engineering | planned |

Every phase keeps the inherited rule: a change reaches `main` only through a pull request with all CI gates green, and
new behaviour arrives with its tests and a traced requirement.

---

## Reviewing this in 5 minutes

1. **Where the store is going:** [Roadmap](docs/store/ROADMAP.md) and the decision to build it on this code,
   [ADR-0010](docs/adr/0010-from-portfolio-to-live-store.md).
2. **Latest build:** the [GitHub Actions runs](https://github.com/rbchy/RbcTcsWorld/actions) (18 gates per push).
3. **Live reports:** [all reports](https://rbchy.github.io/RbcTcsWorld/reports/) · [Allure](https://rbchy.github.io/RbcTcsWorld/) ·
   [Cucumber](https://rbchy.github.io/RbcTcsWorld/cucumber/) · [JaCoCo coverage](https://rbchy.github.io/RbcTcsWorld/coverage/) ·
   [PIT mutation](https://rbchy.github.io/RbcTcsWorld/mutation/) · [Playwright](https://rbchy.github.io/RbcTcsWorld/playwright/)
4. **Defects the tests actually found:** [Defect Reports](docs/qa/DEFECT_REPORTS.md) - 29 entries, each with
   evidence, root cause, fix and regression test.
5. **How release decisions are made:** [Test Strategy](docs/qa/TEST_STRATEGY.md) · [Risk Register](docs/qa/RISK_REGISTER.md) ·
   [Test Summary Report (GO / NO-GO)](docs/qa/TEST_SUMMARY_REPORT.md) · [UAT Plan](docs/qa/UAT_PLAN.md)
   (10 tasks for real users, phase 6 runs it with small real orders)
6. **Why it is built this way:** [Architecture Decision Records](docs/adr/) - ten short records, each with the
   alternatives and the price paid. Commands: `make help` · [Contributing](CONTRIBUTING.md) · [MIT License](LICENSE)

---

## Quality gates

A gate fails the build. Nothing is "reported only" unless the table says so.

| # | Gate | Threshold | Current | Tool |
|---|---|---|---|---|
| 1 | Backend unit + integration | 100 % pass | 772 / 772 | JUnit 5, Mockito, H2 + Flyway |
| 2 | API, database, BDD, UI | 100 % pass | 444 / 444 | REST Assured, JDBC, Cucumber 7, Selenium 4, Mailpit |
| 3 | Line / branch coverage | >= 97 % / >= 88 % | 97.3 % / 94.2 % | JaCoCo |
| 4 | Mutation score | >= 84 % | 89.3 % (test strength 94.7 %) | PIT |
| 5 | API contract, consumer side | every response matches its schema | 12 strict JSON Schemas | json-schema-validator |
| 6 | API contract, provider side | no breaking change vs. approved baseline | 0 (gate has its own self-test) | openapi-diff |
| 7 | Accessibility | 0 serious / critical | 0 on 23 pages | axe-core, WCAG 2.1 A + AA |
| 8 | Cross-browser | 100 % pass | 59 / 59 in Firefox and Edge | Selenium, CI matrix |
| 9 | Performance | SLO thresholds (p95, error rate, no overselling) | within SLO | k6, JMeter (manual workflow) |
| 10 | DAST | 0 Medium / High | 0 | OWASP ZAP API scan |
| 11 | Dependencies | 0 known vulnerabilities, 0 open exceptions | 0 (complete SBOMs, self-test passed) | OSV-Scanner on CycloneDX SBOMs |
| 12 | Containers | stack healthy, smoke tests pass | 63 / 63 | Docker Compose |
| 13 | Traceability | every requirement has a test | 129 / 129 | `docs/qa/check_rtm.py` |
| 14 | Pipeline definition | Jenkinsfile valid | validated by a real Jenkins | declarative linter |
| 15 | Public demo stack | HTTPS, only 80/443 open, demo safeguards, nightly reset | all smoke checks pass | Docker Compose, Caddy, `deploy/smoke.sh` |
| 16 | Playwright (TypeScript) | 100 % pass, visual baselines match | 270 / 270 + 3 skipped by design (Chromium, Firefox, WebKit, Pixel 7, iPhone 14) | Playwright, axe-core |
| 17 | Phone: Android | 100 % pass (one rerun, reported as flaky) | 4 / 4 in Chrome on Android 14 | Appium 3 + UiAutomator2, Android emulator |
| 18 | Phone: iOS | 100 % pass (one rerun, reported as flaky) | 4 / 4 in Safari on the newest iPhone simulator | Appium 3 + XCUITest, macOS runner |

Thresholds are set just under the measured value on purpose: they stop regressions without inviting
tests that exist only to move a number. How the test layers fit together and what each one found is described in
[docs/FOUNDATION.md](docs/FOUNDATION.md).

---

## Before the first real order

These are the gaps between the demo and a live store, in the order the roadmap closes them. Stating them is part of
the plan, not a footnote.

- **Card numbers reach the backend today** (the shop's own card form, Stripe test cards only). With real cards that
  would put the server under the full PCI DSS rules; phase 2 moves the card form to Stripe Elements.
- **Tax is one flat 6 %** and shipping a flat $5.99 with made-up tracking numbers (phase 3).
- **E-mails land in Mailpit**, a test inbox; a mail service, the store's own domain, SPF and DKIM come in phase 3.
- **The server runs in demo mode** with a nightly reset; daily backups, alerts and a tested restore come in phase 5.
- **No business or legal pages yet:** LLC, sales tax license, privacy policy, terms and return policy (phase 4).
- **Products are samples** with drawn pictures until the owner decides what to sell (phase 0).

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

```bash
mvn -f backend/pom.xml verify                                          # unit + integration + coverage gate
mvn -f automation/pom.xml clean test -DexcludedGroups=ui,mobile        # API, contract, DB, BDD, security
mvn -f automation/pom.xml clean test -Dgroups=ui -Dbrowser=edge        # UI: chrome | firefox | edge | safari
cd playwright && npm ci && npx playwright test                         # Playwright, 5 browsers and devices
```

The automation suite needs the backend on 8081 (and the storefront on 5173 for UI and accessibility tests).

---

## Documentation

**Store:** [Roadmap](docs/store/ROADMAP.md) · [ADR-0010: from portfolio to live store](docs/adr/0010-from-portfolio-to-live-store.md) ·
[The inherited foundation](docs/FOUNDATION.md)

**QA documents (English):** [Strategy](docs/qa/TEST_STRATEGY.md) · [Plan](docs/qa/TEST_PLAN.md) ·
[Risks](docs/qa/RISK_REGISTER.md) · [Traceability (129 requirements)](docs/qa/TRACEABILITY_MATRIX.md) ·
[Defects (29)](docs/qa/DEFECT_REPORTS.md) · [Test Summary Report](docs/qa/TEST_SUMMARY_REPORT.md) ·
[UAT Plan](docs/qa/UAT_PLAN.md) · [UAT Summary](docs/qa/UAT_SUMMARY.md)

**Guides (Bangla):** [HELP_BN](docs/HELP_BN.md) to start, and one guide per feature or test layer in
[docs/modules/](docs/modules/) (cart, orders, payments, returns, admin back office, audit trail, Playwright, Appium,
security, mutation testing, CI/CD and more).

## License

[MIT](LICENSE)

---

A store is ready when its customers can trust it, not when its code compiles. This one starts from tests that
already find the expensive defects, and adds what real customers need, one tested step at a time.
