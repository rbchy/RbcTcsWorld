# Test Strategy - RbcTcsWorld E-Commerce Platform

| | |
|---|---|
| Document | Test Strategy (organisation-level: *how* we test this product) |
| Version | 1.0 |
| Owner | RB Chowdhury, QA Lead |
| Applies to | Backend API, storefront UI, database, CI/CD pipeline |
| Related | [Test Plan](TEST_PLAN.md) · [Risk Register](RISK_REGISTER.md) · [Traceability Matrix](TRACEABILITY_MATRIX.md) · [Defect Reports](DEFECT_REPORTS.md) · [Test Summary Report](TEST_SUMMARY_REPORT.md) |

## 1. Purpose

This strategy defines how quality is built into and verified on the RbcTcsWorld e-commerce platform:
which risks matter most, which test levels and types address them, which tools are used, and which
automated quality gates a change must pass before it can reach `main`.

## 2. Product under test

An Amazon-style shop: registration and login (JWT), catalog and search, cart, checkout with coupons,
tax and shipping, a mock card-payment gateway, order lifecycle (place, pay, ship, deliver, cancel),
public parcel tracking, returns with refund and restock rules, verified-purchase reviews and ratings,
and a wishlist.

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1 (Spring 7, Jackson 3, Tomcat 11), Spring Security (JWT), Spring Data JPA, Flyway (V1-V7) |
| Database | PostgreSQL 16 (H2 in PostgreSQL mode for integration tests) |
| Frontend | React 18 + Vite, every testable element exposes a `data-testid` |
| Delivery | GitHub Actions CI, Allure report on GitHub Pages |

## 3. Quality goals

1. **Money is always right.** Totals, discounts, tax, refunds and coupon usage are exact to the cent.
2. **Stock is never oversold**, even when many customers buy the last units at the same moment.
3. **Customers only see and change their own data** (cart, orders, reviews, wishlist).
4. **No known exploitable vulnerability** in our code or in the libraries we ship.
5. **Fast enough at peak:** p95 below 500 ms for reads and 1000 ms for writes with 50 concurrent users.
6. **Every rule is executable:** each business rule is covered by at least one automated test that runs on every push.

## 4. Approach: risk-based testing

Test effort follows risk = likelihood x impact (see [Risk Register](RISK_REGISTER.md)).
High-risk areas (payments, stock, access control, authentication) get tests on several levels
**and** a non-functional test (concurrency, security, performance). Low-risk areas (catalog
browsing, wishlist cosmetics) get API tests plus one UI smoke path.

Design techniques used deliberately:

| Technique | Where it is applied |
|---|---|
| Boundary value analysis | cart quantity 0/1/10/11, stock = qty and qty + 1, free shipping 49.99/50.00, coupon minimum 24.99/25.00, return window "30 days" vs "30 days + 1 s", rating 0/1/5/6, title 100/101, body 2000/2001 |
| Equivalence partitioning | coupon states (valid, unknown, expired, future, disabled), payment cards (approved, declined, insufficient funds, bad Luhn, expired) |
| Decision table | return reason -> refund amount and restock (DAMAGED, WRONG_ITEM, NOT_AS_DESCRIBED, NO_LONGER_NEEDED) |
| State transition | order state machine: every allowed and every forbidden transition is in a parameterised table |
| Access-control matrix | 19 endpoints x {anonymous, customer, admin} with the exact expected status |
| Error guessing / attack | JWT `alg:none`, edited payload, guessed secret, IDOR, mass assignment, SQL injection, path traversal, oversized input |
| Concurrency | 10 buyers for 5 units, 100-300 buyers for 20-50 units (k6), 8 simultaneous reviews |

## 5. Test levels (the test pyramid)

```
                 UI (Selenium, 12)            <- user journeys only
            BDD (Cucumber, 43 scenarios)       <- business-readable acceptance
        API + DB + security (REST Assured, JDBC) <- most functional coverage
   Integration (Spring MockMvc + H2)              <- every endpoint, real wiring
Unit (JUnit 5 + Mockito)                            <- rules, maths, state machine
```

| Level | Scope | Runs against | Owner |
|---|---|---|---|
| Unit | One class: pricing maths, state machine, coupon rules, rating average, lockout timing (injected `Clock`) | Nothing (mocks) | Developer + QA review |
| Integration | Whole Spring application, real HTTP layer and security filters, H2 database with the real Flyway migrations | In-memory | Developer |
| API | Black-box HTTP tests through `ApiClient` subclasses (REST Assured), fixtures created only through the public API | Running backend + PostgreSQL | QA |
| Database validation | Read-only JDBC checks of rows and invariants after API actions (`@Tag("db")`) | PostgreSQL | QA |
| BDD acceptance | Gherkin scenarios in the language of the business; one shared `ScenarioContext` per scenario (PicoContainer) | Running backend | QA + Product |
| UI end-to-end | Page Object Model, `data-testid` locators, explicit waits only; slow preconditions are created through the API (hybrid tests) | Backend + frontend + headless Chrome | QA |
| Performance | k6: smoke, load, stress, spike, soak, flash sale, with thresholds as SLOs | Running backend | QA |
| Security | Attack tests (`@Tag("security")`), OWASP ZAP API scan, OSV-Scanner dependency scan | Running backend, source tree | QA |

**Why so few UI tests:** UI tests are the slowest and most fragile. Every rule is already proven
below the UI; UI tests only prove that the screens are wired to those rules (a coupon shows the
right total, a declined card shows the error, a return can be requested).

## 6. Test types and their tools

| Type | Tool | Evidence |
|---|---|---|
| Functional API | REST Assured 5.5, JUnit 5 | Allure: request/response of every call |
| Acceptance (BDD) | Cucumber 7.18 | Allure: Gherkin steps; `cucumber-report.html` |
| UI | Selenium 4.49, Chrome headless | Allure: screenshot + URL on failure |
| Data integrity | JDBC (PostgreSQL driver) | Allure: SQL results in assertions |
| Performance | k6 | `performance/reports/*-report.html`, CI annotations |
| Security - attack tests | REST Assured, hand-crafted JWTs (Base64 + HMAC) | Allure Epic "Security" |
| Security - DAST | OWASP ZAP API scan from the OpenAPI spec (`/v3/api-docs`), logged in as a customer | `zap-report` artifact |
| Security - SCA | OSV-Scanner on Maven-resolved CycloneDX SBOMs and `package-lock.json`, with completeness check and self-test | CI annotations with "fixed in" version |
| Reporting | Allure 2.29 with history/trend, failure categories | https://rbchy.github.io/RbcTcsWorld_ECommerceSuite/ |

## 7. Test environments

| Environment | Purpose | Data |
|---|---|---|
| Developer laptop (macOS, Eclipse) | Write and debug tests; `docker compose up -d` starts PostgreSQL | Seeded by Flyway + `DataSeeder` |
| CI (GitHub Actions, Ubuntu, 2 CPU) | Every push: all levels, quality gates | Fresh PostgreSQL service per run |
| CI - manual performance workflow | Load / stress / spike / soak on demand | Fresh database |

Ports: backend 8081 (8080 is kept free for Jenkins), frontend 5173.

## 8. Test data management

* **Every test creates its own data** through the public API (`Fixtures.product(price, stock)`,
  `Fixtures.verifiedBuyer(productId)`, unique e-mails and SKUs). Tests never depend on each other
  or on execution order, and can run on a database that already contains millions of rows.
* **Seed data** is used only where the business defines it: the admin account and the coupons
  (`WELCOME10`, `SAVE5`, `EXPIRED20`, `FUTURE15`, `DISABLED`).
* **Test cards** follow the mock gateway: `4242...4242` approved, `4000...0002` declined,
  `4000...9995` insufficient funds.
* **The database is never written by tests directly**: DB tests are read-only checks, so a test
  can never create a state the application itself could not create.
* **Time-dependent rules** (coupon validity, return window, lockout) are tested with an injected
  `Clock` in unit tests instead of waiting.

## 9. Quality gates (Definition of Done for a change)

A push to `main` is green only if all of these pass (`.github/workflows/ci.yml`; the `Jenkinsfile` runs the same gates):

| # | Gate | Fails when |
|---|---|---|
| 1 | Backend unit + integration tests | any test fails |
| 2 | API, DB, BDD and UI tests against PostgreSQL | any test fails |
| 3 | k6 smoke | read p95 >= 500 ms, write p95 >= 1000 ms, errors >= 1 %, checks < 99 % |
| 4 | k6 flash sale | orders created != stock, any 5xx, stock ends != 0 |
| 5 | OWASP ZAP API scan | a FAIL rule fires (injection, XSS, path traversal, stack traces, missing security headers) |
| 6 | OSV-Scanner | any dependency with CVSS >= 9.0 that is not accepted in `osv-scanner.toml` (reason + guard test + expiry) |
| 7 | JaCoCo coverage | line coverage < 97 % or branch coverage < 88 % (measured 97.5 % / 88.3 %) |
| 8 | PIT mutation testing | mutation score of the business logic < 84 % (measured 84.9 %; was 65.5 % before the tests it found) |
| 9 | Traceability | the RTM references a test that does not exist (`check_rtm.py`) |
| 10 | Docker | images do not build, a container is not healthy, or the smoke tests fail against the containers |
| 11 | Jenkinsfile | the declarative validator of a real Jenkins rejects the pipeline |
| 12 | API contract | a response breaks its JSON Schema, or the OpenAPI description has a breaking change against `docs/api/openapi.json` |
| 13 | Accessibility | a storefront page has a serious or critical WCAG 2.1 A/AA violation (axe-core) |
| 14 | Cross-browser | a UI journey or accessibility check fails in Firefox or Edge (Chrome: gate 2) |

Reports produced on every run: Allure (published), JUnit XML, Cucumber HTML, k6 HTML/JSON,
ZAP HTML/JSON, OSV JSON.

## 10. Entry and exit criteria

**Entry (testing of a feature starts when):** acceptance rules are written down, the API contract
is visible in `/v3/api-docs`, the backend builds, and the database migration is in place.

**Exit (a feature is done when):**
* every requirement of the feature is linked to at least one passing test in the [Traceability Matrix](TRACEABILITY_MATRIX.md);
* all quality gates are green;
* no open defect of severity Critical or High;
* Medium defects have an owner and a decision (fix now / accept with reason);
* the Bangla module guide is updated.

## 11. Defect management

Defects are recorded with the template in [Defect Reports](DEFECT_REPORTS.md): steps to
reproduce, expected vs actual, evidence, **root cause**, fix, and the **regression test** that now
guards it. Every product defect gets a regression test before it is closed.

| Severity | Meaning | Example |
|---|---|---|
| Critical | Security breach, money or stock wrong, data loss | dependency with CVSS 9.8 |
| High | Main flow blocked or exploitable weakness without workaround | no login rate limit |
| Medium | Rule wrong in an edge case, degraded performance, information leak | timing reveals accounts, catalog without pagination |
| Low | Cosmetic, header hardening, wrong rounding of a hint | `Retry-After` 899 instead of 900 |

Priority (P1-P3) is set separately by business urgency.

Test-code defects (flaky waits, platform-specific typing) are tracked the same way: a flaky
test is a defect, not "just re-run it".

## 12. Metrics

| Metric | Source |
|---|---|
| Tests run / passed / failed / skipped per level | CI "Test totals" annotations, Allure |
| Requirement coverage | [Traceability Matrix](TRACEABILITY_MATRIX.md) (checked automatically by `check_rtm.py`) |
| Defects by severity, by layer that found them, by type (product / test) | [Defect Reports](DEFECT_REPORTS.md) |
| p95 / p99 latency, throughput, error rate | k6 reports |
| Vulnerable dependencies, ZAP alerts by risk | CI annotations |
| Pass-rate trend and duration trend | Allure history |

## 13. Roles

| Role | Responsibility |
|---|---|
| QA Lead (RB Chowdhury) | Strategy, risk register, automation framework, CI gates, reporting, sign-off |
| Developers | Unit and integration tests, fix defects, keep `data-testid` hooks stable |
| Product owner | Acceptance rules, priority of defects, accepts residual risks |

## 14. Known limitations and accepted risks

* Payments use a **mock gateway**; a real provider would need sandbox contract tests.
* Lockout state is in **Redis** (`LOGIN_STORE=redis`); while Redis is down each instance counts in memory.
* Logout revokes the refresh token at once; a stolen **access** token stays valid until it expires (15 minutes).
* Tomcat and Jackson are pinned to patch releases newer than the ones Spring Boot 4.1.1 manages (OSV
  advisories); remove each pin when Boot ships that version (CI "Versions" annotations show it).
* Performance numbers from the 2-CPU CI runner are for **comparison between runs**, not capacity planning.
