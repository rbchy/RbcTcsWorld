# Test Summary Report - Release 1.0

| | |
|---|---|
| Release | 1.3 (Modules 0-5, storefront UI, performance and security hardening; Spring Boot 4.1; API contract, accessibility, cross-browser) |
| Build | branch `feature/cross-browser` -> `main`, CI on 2026-10-08 (GitHub Actions); Jenkins build #10 green |
| Prepared by | RB Chowdhury, QA Lead |
| Plan | [Test Plan](TEST_PLAN.md) |

## 1. Recommendation

**GO for release 1.0.** All exit criteria are met, every requirement is covered, and no defect is open.
The last open defect, [DEF-007](DEFECT_REPORTS.md#def-007) (catalog without pagination), was fixed test-first
before sign-off: response size -91 %, constant regardless of catalog size, and on the large local
catalog the endpoint p95 dropped from 22 ms to 9 ms (-59 %).

## 2. Exit criteria

| # | Criterion | Target | Result | |
|---|---|---|---|---|
| 1 | Automated tests on CI | 100 % pass | **662 / 662** passed, 0 skipped (+44 smoke against Docker) | ✅ |
| 2 | Requirement coverage | >= 95 % | **63 / 63 = 100 %** | ✅ |
| 3 | Open Critical / High defects | 0 | **0** | ✅ |
| 4 | k6 smoke + load SLOs | all pass | all pass | ✅ |
| 5 | k6 flash sale | orders = stock, stock 0 | 15 / 15 (CI), 50 / 50 (local, 300 buyers) | ✅ |
| 6 | OWASP ZAP | 0 Medium / High | **0** (Low only, informational) | ✅ |
| 7 | OSV-Scanner | 0 unaccepted CVSS >= 9 | **0 open, 0 accepted** (was 21; both Spring CVEs fixed by the Boot 4 upgrade) | ✅ |
| 8 | Code coverage (JaCoCo) | >= 97 % lines, >= 88 % branches | **98.0 % lines, 91.9 % branches** | ✅ |
| 9 | Mutation score (PIT, business logic) | >= 84 % | **89.2 %** (was 65.5 %); test strength 99.0 % | ✅ |
| 10 | API contract | 12 schemas match; no breaking OpenAPI change | **6/6 contract tests, OpenAPI unchanged (34 paths)** | ✅ |
| 11 | Accessibility (WCAG 2.1 AA, axe-core) | 0 serious/critical on every page | **13/13 pages clean** (the first 8 all failed before DEF-018) | ✅ |
| 12 | Cross-browser (Firefox, Edge) | all UI tests pass in each browser | **34/34 Firefox 157, 34/34 Edge 154** (Chrome in criterion 1) | ✅ |

## 3. Test execution

| Suite | Tests | Passed | Failed | Skipped | Runs against |
|---|---|---|---|---|---|
| Backend unit + integration | 352 | 352 | 0 | 0 | H2 (PostgreSQL mode) + Flyway |
| Automation: API, DB, security, BDD, UI | 310 | 310 | 0 | 0 | Backend + PostgreSQL 16 + headless Chrome |
| **Total** | **662** | **662** | **0** | **0** | |
| UI journeys + accessibility in Firefox and Edge (separate CI jobs) | 68 | 68 | 0 | 0 | headless Firefox 157 / Edge 154 |
| Smoke against the Docker images | 44 | 44 | 0 | 0 | `docker compose --profile app` (postgres + redis + backend + nginx storefront) |

The automation total contains 43 Cucumber scenarios, 21 Selenium UI journeys, 13 accessibility page checks and 6 API contract tests; the rest are API,
database and security tests. Figures come from the CI "Test totals" annotations (surefire XML).

## 4. Performance

| Test | Environment | Load | Key results | Thresholds |
|---|---|---|---|---|
| Load | CI runner (2 CPU) | 40 browsing + 10 buying users, 8 min | 18,701 requests, 0 % failed, read p95 2.2 ms, write p95 7.2 ms, purchase journey p95 28 ms, 786 orders paid | all pass |
| Load | MacBook (Docker) | same | 18,641 requests, 0 % failed, p95 20 ms, purchase journey p95 81 ms, 771 orders paid | all pass |
| Flash sale | MacBook | 300 buyers, 50 units, same moment | 50 created, 250 rejected (409), 0 unexpected, stock 0; order p95 279 ms | all pass |
| Flash sale | MacBook | 100 buyers, 20 units | 20 created, 80 rejected, stock 0; order p95 112 ms | all pass |
| Smoke + flash sale | CI, every push | 2 users; 60 buyers / 15 units | 0 % failed, checks 100 % | all pass |
| Catalog (DEF-007) | CI, every push | 10 visitors, 20 s | response 32,927 -> 2,998 bytes p95 (234 -> 20 products) | all pass |
| Load after DEF-007 fix | MacBook (Docker) | 40 + 10 users, 8 min | 18,645 requests, 0 % failed, p95 16 ms; `GET /api/products` p95 22 -> 9 ms (-59 %) | all pass |

Observation: order latency grows with the number of simultaneous buyers of the **same** product
(112 ms -> 279 ms from 100 to 300 buyers) because the row update is serialised. This is the expected
cost of correctness and is far below the 3-second target.

## 5. Security

| Activity | Result |
|---|---|
| Code review + attack tests | 2 High/Medium issues found and fixed (brute force DEF-003, timing enumeration DEF-004) |
| JWT attacks (alg none, edited payload, guessed secret, malformed headers) | all rejected |
| Access-control matrix (19 endpoints x 3 roles) | all as designed |
| Injection, path traversal, oversized input, malformed bodies | no success, no 5xx |
| OWASP ZAP API scan (OpenAPI, logged in) | 0 Medium/High; 1 Low fixed (CORP header) |
| OSV-Scanner | 21 vulnerable packages / 85 advisories -> 0 (Spring Boot 3.5.16 + pins); after the Boot 4.1 upgrade 0 with no accepted exceptions |

## 6. Defects

| Severity | Found | Fixed | Open |
|---|---|---|---|
| Critical | 1 | 1 | 0 |
| High | 5 | 5 | 0 |
| Medium | 10 | 10 | 0 |
| Low | 3 | 3 | 0 |
| **Total** | **19** | **19** | **0** |

11 product defects, 5 test-code defects, 3 test-infrastructure defects. Every closed product defect has a regression test.
Details: [Defect Reports](DEFECT_REPORTS.md).

## 7. Residual risks accepted for release 1.0

| Risk | Why accepted | Follow-up |
|---|---|---|
| Login lock falls back to per-instance memory while Redis is down | Logins keep working; each instance still locks after 5 failures | Alert on the outage counter |
| Logged-out access token valid until expiry (max 15 min) | Refresh token is revoked at once; a denylist costs a lookup per request | Denylist if a shorter window is required |
| Tomcat 11.0.26 / Jackson 3.1.7 pinned above Boot 4.1.1 | Fix advisories in the managed versions | Remove the pins when Boot ships them |
| 8 surviving mutants (PIT) | 5 are equivalent (cannot change behaviour, e.g. Luhn `d > 9` vs `d >= 9`), 3 are defensive or housekeeping code | Reviewed one by one in docs/modules/MUTATION_BN.md |
| Stripe behaviour only proven with a test key | stripe-mock checks every request; declines come from recorded answers | Add `STRIPE_TEST_SECRET_KEY` to GitHub secrets |
| E-mail at least once (rare duplicate after a crash) | Losing a confirmation is worse than a duplicate | None planned |
| Registration reveals that an e-mail exists | Usability; common e-commerce trade-off | Re-evaluate with product owner |

## 8. Lessons learned

1. **Assert state, not just status codes:** a 200 response hid that product updates were ignored (DEF-001).
2. **Run on the CI operating system early:** a Mac-only green suite hid a Linux typing bug (DEF-008).
3. **Fix flaky tests at the root:** stale-element handling instead of retries (DEF-009).
4. **Performance reports find design problems:** the per-endpoint table exposed the missing pagination (DEF-007),
   which was then fixed test-first: tests red on the unfixed code, green after the fix, before/after measured
   in the same environment.
5. **Verify that a "fixed" version really exists:** an advisory pointed to an unpublished Tomcat release (DEF-005).
6. **Test the tests:** 96 % line coverage hid a test that could not fail (DEF-013). Mutation testing raised the
   score from 65.5 % to 84.9 % with 30 targeted tests, and branch coverage from 78.8 % to 86.6 % as a side effect.
7. **Upgrade on a branch behind every gate:** the Spring Boot 4 move was merged only when all jobs were green;
   the gates found a critical Tomcat CVE, a UI race (DEF-016) and a Tomcat 11 regression only a real server shows (DEF-017).
8. **Make CI explain itself:** failures, totals, k6, ZAP and OSV results are annotations on the run page,
   so a red build can be understood without downloading logs.
