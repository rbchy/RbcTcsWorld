# Test Plan - Release 1.0 (Modules 0-5)

| | |
|---|---|
| Document | Test Plan (release-level: *what* is tested for this release, when, by whom, and when it is done) |
| Release | 1.0 - complete shop: identity, catalog, cart, checkout, payments, fulfilment, returns, reviews, wishlist |
| Owner | RB Chowdhury, QA Lead |
| Based on | [Test Strategy](TEST_STRATEGY.md), [Risk Register](RISK_REGISTER.md) |
| Results | [Test Summary Report](TEST_SUMMARY_REPORT.md) |

## 1. Objective

Confirm that release 1.0 meets its business rules and non-functional targets, that no Critical or High
defect is open, and give a documented go / no-go recommendation.

## 2. Scope

### In scope

| Feature | Module | Main risks (Risk Register) |
|---|---|---|
| Registration, login, JWT, roles, brute-force lockout | 0, Security | R-04, R-05, R-06, R-07 |
| Catalog, search, admin product management | 0 | R-17, R-18 |
| Cart | 1 | R-03 |
| Orders, stock reservation, cancel, audit trail | 2 | R-02, R-10 |
| Price breakdown, coupons, mock payment gateway, refunds | 3 | R-01, R-08, R-09, R-12 |
| Shipping, tracking timeline, public tracking, returns | 4 | R-10, R-11 |
| Verified-purchase reviews, ratings, moderation, wishlist | 5 | R-13, R-14 |
| Storefront UI for all of the above | Frontend | R-19 |
| Performance (load, rush), security (DAST, SCA), data integrity | Steps 4-5 | R-02, R-15, R-16, R-18 |

### Out of scope (and why)

| Item | Reason |
|---|---|
| Real payment provider | Mock gateway in this release; contract tests against a provider sandbox in a later release |
| E-mail / SMS notifications | Not implemented |
| Admin UI | Admin functions are API-only; covered by API and access-control tests |
| Mobile apps, Safari in CI | Chrome gates every build; Firefox and Edge run as a CI matrix; Safari only on a Mac (Jenkins `BROWSER` parameter), no headless mode |
| Manual accessibility audit (keyboard-only, screen reader, 200 % zoom) | Automated axe-core WCAG 2.1 AA checks gate every build; the manual part stays on the release checklist |
| Production capacity planning | CI runner numbers are for comparison only |

## 3. Test items and versions

| Item | Version |
|---|---|
| Backend | `rbctcsworld-shop-backend` 1.0.0, Spring Boot 4.1.1, Flyway V1-V7 |
| Frontend | `rbctcsworld-shop-frontend` 1.0.0 |
| Automation | `rbctcsworld-shop-automation` 1.0.0 (JUnit 5.11, REST Assured 5.5, Selenium 4.49, Cucumber 7.18, Allure 2.29) |
| Performance | k6 scripts in `performance/` |

## 4. Approach per feature

Each feature is tested bottom-up, following the strategy:

1. **Unit:** rules and maths in isolation (boundaries, decision tables, state transitions).
2. **Integration:** the endpoint through the real Spring stack and the real migrations.
3. **API + DB:** black-box against PostgreSQL, then read-only checks of the rows written.
4. **BDD:** the main business scenarios in Gherkin, readable by the product owner.
5. **UI:** one or two journeys per feature, preconditions created through the API.
6. **Non-functional:** concurrency and k6 for stock, money and ratings; security tests for every endpoint
   that takes user input or ownership.

Every requirement is listed with its tests in the [Traceability Matrix](TRACEABILITY_MATRIX.md).

## 5. Test environment

| Item | Value |
|---|---|
| CI | GitHub Actions `ubuntu-latest`, Java 21 (Temurin), Node 20, PostgreSQL 16 service container, Chrome headless |
| Local | macOS, Eclipse, Docker Desktop (`docker compose up -d`), backend `:8081`, frontend `:5173` |
| Accounts | Admin seeded by `DataSeeder`; customers created per test |
| Data | Per-test data via API fixtures; seeded coupons; mock test cards |
| Tools | Allure, k6, OWASP ZAP (container), OSV-Scanner (action pinned by commit SHA) |

## 6. Schedule (phases)

| Phase | Content | Exit of phase |
|---|---|---|
| 1. Foundation | Roles, JSON errors, status codes, seed data, framework skeleton | API + unit green |
| 2. Feature modules 1-5 | Build each module with its tests, Bangla guide per module | Module tests green in CI |
| 3. Reporting | Allure with trend and failure categories, published on Pages | Report online |
| 4. UI | Storefront pages, page objects, UI journeys in CI | UI green on Linux CI |
| 5. Performance | k6 suite, SLO thresholds, flash-sale gate | Thresholds pass |
| 6. Security | Code review, attack tests, ZAP, OSV, fixes | 0 Critical/High open, OSV 0 vulnerable packages |
| 7. Sign-off | Traceability check, summary report, go / no-go | This plan's exit criteria met |

## 7. Entry criteria

* Feature rules written and agreed (module guide).
* Backend builds; migrations apply on an empty database.
* API visible in `/v3/api-docs`; UI exposes `data-testid` hooks for new screens.

## 8. Exit criteria (release)

| # | Criterion | Target |
|---|---|---|
| 1 | Automated tests pass on CI (backend, API, DB, BDD, UI) | 100 % |
| 2 | Requirement coverage in the RTM (`check_rtm.py`) | >= 95 %, every gap has a defect and a decision |
| 3 | Open Critical / High defects | 0 |
| 4 | k6 smoke and load SLOs | all thresholds pass |
| 5 | k6 flash sale | orders = stock, 0 unexpected statuses, stock 0 |
| 6 | OWASP ZAP | 0 Medium / High alerts |
| 7 | OSV-Scanner | 0 fixable vulnerabilities with CVSS >= 9.0 |

## 9. Suspension and resumption

Testing of a build is **suspended** when the backend does not start, migrations fail, or more than 20 %
of API tests fail for the same environmental reason (Allure category "Environment: backend not running").
It **resumes** when a fresh CI run on the fixed build passes the backend job.

## 10. Deliverables

| Deliverable | Where |
|---|---|
| Test strategy, plan, risk register, RTM, defect reports, summary report | `docs/qa/` |
| Automated tests | `backend/src/test`, `automation/src/test`, `performance/` |
| Allure report with history | GitHub Pages |
| k6, ZAP, OSV reports | CI artifacts and annotations |
| Module guides (Bangla) | `docs/modules/` |

## 11. Risks to the test effort

| Risk | Mitigation |
|---|---|
| Flaky UI tests block releases | Explicit waits, stale-safe page objects, API-built preconditions (DEF-008, DEF-009) |
| CI-only failures cannot be debugged without logs | Failures, Maven errors, test totals, k6, ZAP and OSV results are written as run annotations |
| Advisory names a fixed version that is not published | CI lists the newest published version of each pinned library (DEF-005) |
| Single QA engineer | Everything automated and gated in CI; documentation kept next to the code |

## 12. Approval

| Role | Name | Decision |
|---|---|---|
| QA Lead | RB Chowdhury | See [Test Summary Report](TEST_SUMMARY_REPORT.md) |
| Product owner | - | |
