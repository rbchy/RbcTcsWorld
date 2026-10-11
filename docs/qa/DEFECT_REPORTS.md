# Defect Reports

Real defects found while building and testing this platform. Every **product** defect was closed
only after a regression test was added; the test is named in the report. Test-code defects
(flaky or platform-dependent tests) are tracked the same way: a flaky test is a defect.

## Defect log

| ID | Title | Type | Severity | Priority | Found by | Status |
|---|---|---|---|---|---|---|
| [DEF-001](#def-001) | Product update changes only the stock, other fields silently ignored | Product | High | P1 | API test (CRUD lifecycle) | Closed |
| [DEF-002](#def-002) | Unknown product id returns 500 instead of 404 | Product | Medium | P2 | API negative test | Closed |
| [DEF-003](#def-003) | No limit on failed logins (brute force possible) | Product - security | High | P1 | Code review + attack test | Closed |
| [DEF-004](#def-004) | Response time reveals whether an e-mail has an account | Product - security | Medium | P2 | Code review + timing test | Closed |
| [DEF-005](#def-005) | Shipped libraries with known critical CVEs (Tomcat CVSS 9.8) | Product - security | Critical | P1 | OSV-Scanner in CI | Closed |
| [DEF-006](#def-006) | Missing security headers (CSP, Referrer-Policy, CORP) | Product - security | Low | P3 | Header test + OWASP ZAP | Closed |
| [DEF-007](#def-007) | Product catalog has no pagination, response grows with every product | Product - performance | Medium | P2 | k6 load test | Closed |
| [DEF-008](#def-008) | UI tests type an extra "a" into every field on Linux | Test code | High | P1 | CI (Linux) only | Closed |
| [DEF-009](#def-009) | Flaky "stale element reference" in the purchase journey | Test code | Medium | P2 | CI, intermittent | Closed |
| [DEF-010](#def-010) | `Retry-After` says 899 s, but the lock lasts 900 s | Product | Low | P3 | Integration test in CI | Closed |
| [DEF-011](#def-011) | Cucumber run fails with DuplicateStepDefinitionException | Test code | Medium | P2 | Local run (Mac) | Closed |
| [DEF-012](#def-012) | `-Dgroups=ui` run fails: Cucumber suite "did not discover any tests" | Test code | Low | P3 | Local run (Mac) | Closed |
| [DEF-013](#def-013) | "Paid order cannot be paid again" test passed even when the card was charged | Test code | High | P1 | Mutation testing (PIT) | Closed |
| [DEF-014](#def-014) | Jenkins on macOS: `docker: command not found` | Test infrastructure | Medium | P2 | First Jenkins run (Mac) | Closed |
| [DEF-015](#def-015) | OWASP ZAP crashes on Apple Silicon (DOM XSS rule starts Firefox) | Test infrastructure | Medium | P2 | Jenkins run (Mac, ARM) | Closed |
| [DEF-016](#def-016) | Storefront search shows results of an older search when typing fast | Product - UI | Medium | P2 | Selenium UI test (during the Spring Boot 4 upgrade) | Closed |
| [DEF-017](#def-017) | Query string with a parameter without a name returns 500 | Product - regression | Medium | P2 | OWASP ZAP + API test against the real server | Closed |
| [DEF-018](#def-018) | Storefront fails WCAG 2.1 AA on every page (contrast, labels, page language) | Product - accessibility | High | P1 | axe-core accessibility tests (written first) | Closed |
| [DEF-019](#def-019) | Edge does not start in CI ("Chrome instance exited"), Firefox fine | Test infrastructure | Medium | P2 | New cross-browser CI job | Closed |
| [DEF-020](#def-020) | Edge and Firefox UI tests cannot get a driver on the Jenkins Mac (Selenium Manager) | Test infrastructure | Medium | P2 | Jenkins run with the BROWSER parameter | Closed |
| [DEF-021](#def-021) | Dependency scan silently missed 5 vulnerable libraries (one CVSS 9.1) when Maven Central rate-limited it | Test infrastructure | High | P1 | Runner canary (Ubuntu 26) | Closed |
| [DEF-022](#def-022) | Jenkins QA Reports page said "all gates passed" for a build that failed in its first stage | Test infrastructure (reporting) | High | P1 | Reviewing build #30 | Closed |

**Where defects were found** - one reason each test layer exists:

| Found by | Defects |
|---|---|
| API tests | DEF-001, DEF-002 |
| Security review + attack tests | DEF-003, DEF-004 |
| Dependency scan (OSV) | DEF-005 |
| DAST (ZAP) + header tests | DEF-006 |
| Performance (k6) | DEF-007 |
| CI on a different OS / repeated runs | DEF-008, DEF-009, DEF-010 |
| Running the suite like a user would (local, filters) | DEF-011, DEF-012 |
| Mutation testing (tests of the tests) | DEF-013 |
| A second CI system on another CPU architecture (Jenkins, Mac ARM) | DEF-014, DEF-015 |
| Full regression during a framework upgrade (Spring Boot 4) | DEF-016, DEF-017 |
| Accessibility tests (axe-core, WCAG 2.1 AA) | DEF-018 |
| Cross-browser CI (Firefox, Edge) | DEF-019, DEF-020 |
| Runner canary (next CI image) | DEF-021 |
| Checking a report against the build log | DEF-022 |

---

## DEF-001
**Product update changes only the stock, other fields silently ignored**

| Field | Value |
|---|---|
| Severity / Priority | High / P1 - prices cannot be corrected, wrong prices reach customers |
| Component | `product/Product.update`, `PUT /api/products/{id}` |
| Environment | Original project skeleton, any database |

**Steps to reproduce**
1. Log in as admin.
2. `PUT /api/products/{id}` with a new name, SKU, category, price **and** stock.
3. `GET /api/products/{id}`.

**Expected:** all five fields have the new values. **Actual:** only `stock` changed; name, SKU,
category and price kept their old values. The API still answered 200, so the error was silent.

**Root cause:** `update()` in the entity copied only the stock field.
**Fix:** `update()` applies every editable field; SKU uniqueness is checked against other products.
**Regression tests:** `ProductServiceTest#updateChangesEveryField_regressionForStockOnlyBug`,
`ProductApiTest#adminCrudLifecycle`, `ApiIntegrationTest#adminUpdateChangesAllFieldsAndSoftDeleteHidesProduct`.
**Lesson:** a 200 response proves nothing; assert the stored state after every write.

---

## DEF-002
**Unknown product id returns 500 instead of 404**

| Field | Value |
|---|---|
| Severity / Priority | Medium / P2 - wrong status, internal error leaks to clients, monitoring sees false 5xx |
| Component | Product lookup, global error handling |

**Steps:** `GET /api/products/99999999` (no token needed).
**Expected:** 404 with a JSON error body. **Actual:** 500 Internal Server Error.

**Root cause:** the lookup threw an exception for a missing row that no handler translated into an HTTP status.
**Fix:** `NotFoundException` + `GlobalExceptionHandler` returning a uniform `ApiError` JSON (400/401/402/403/404/409/429/500).
**Regression tests:** `ApiIntegrationTest#missingProductIs404NotA500`, `ProductApiTest#missingProduct`,
`ProductApiTest#badId`, scenario "Unknown product returns 404 with a JSON error".

---

## DEF-003
**No limit on failed logins (brute force possible)**

| Field | Value |
|---|---|
| Severity / Priority | High / P1 - unlimited password guessing against any account, including the admin |
| Component | `POST /api/auth/login`, `AuthService` |
| Found by | Security code review (step 5), confirmed with a test |

**Steps:** send 100 logins with wrong passwords for one e-mail.
**Expected:** after a small number of failures the account is temporarily locked (HTTP 429).
**Actual:** every attempt answered 401; guessing could continue forever.

**Fix:** `LoginAttemptService`: 5 failures per e-mail within 15 minutes lock that e-mail for 15 minutes.
While locked every login (also with the correct password) gets 429 + `Retry-After`. Unknown e-mails
are counted the same way, so the lock does not reveal which accounts exist.
**Regression tests:** `LoginAttemptServiceTest` (7 tests with a controllable clock),
`SecurityIntegrationTest#fiveWrongPasswordsLockTheAccountEvenForTheRightPassword`,
`AuthenticationAttackTest#bruteForceLockout`, `AuthenticationAttackTest#lockoutDoesNotEnumerate`.
**Accepted residual risk:** someone who knows a customer's e-mail can lock it for 15 minutes
(temporary lock chosen to keep this small). The counter is in memory; several instances need Redis.

---

## DEF-004
**Response time reveals whether an e-mail has an account**

| Field | Value |
|---|---|
| Severity / Priority | Medium / P2 - account enumeration helps phishing and credential stuffing |
| Component | `AuthService.login` |

**Steps:** time 10 logins with a wrong password for a registered e-mail and 10 for unknown e-mails.
**Expected:** both take about the same time (the message is already identical).
**Actual:** unknown e-mail ~1 ms (no password check), registered e-mail ~90 ms (bcrypt). The difference
is easy to measure over the internet.

**Root cause:** the method returned immediately when the user was not found, skipping bcrypt.
**Fix:** for an unknown e-mail a bcrypt check against a dummy hash still runs, so both paths cost the same.
**Regression tests:** `AuthServiceTest#unknownEmailStillRunsAPasswordCheckSoTimingRevealsNothing`,
`AuthenticationAttackTest#noTimingEnumeration` (compares the median of both groups).

---

## DEF-005
**Shipped libraries with known critical CVEs**

| Field | Value |
|---|---|
| Severity / Priority | Critical / P1 |
| Component | `backend/pom.xml` (Spring Boot 3.5.6 and its managed libraries), `automation/pom.xml` |
| Found by | OSV-Scanner, added to CI in step 5 |

**Actual:** 21 vulnerable packages with 85 advisories, including `tomcat-embed-core` 10.1.46 (CVSS 9.8),
`spring-security-web` 6.5.5 (CVSS 9.1), `jackson-core`, `jackson-databind`, `postgresql`, `logback`.

**Fix (in steps):**
1. Spring Boot 3.5.6 -> 3.5.16 (last open-source 3.5 patch): 21 -> 8 packages.
2. Pinned patched versions on the same release line: Tomcat 10.1.60, Jackson 2.21.7,
   PostgreSQL driver 42.7.13, Log4j API 2.25.5, commons-lang3 3.18.0: 8 -> **0**.

**Complication worth knowing:** the advisory named Tomcat **10.1.58** as the fix, but that version was
never published to Maven Central (the build failed: "Could not find artifact"). A CI step now lists the
newest *published* version of each pinned library; 10.1.60 was used.
**Prevention:** OSV-Scanner runs on every push and **fails the build** when a dependency with CVSS >= 9.0
has a fixed version available.

---

## DEF-006
**Missing security headers**

| Field | Value |
|---|---|
| Severity / Priority | Low / P3 - defence in depth (clickjacking, referrer leaks, cross-origin reads) |

**Actual:** responses had no `Content-Security-Policy`, `Referrer-Policy`, `Permissions-Policy`;
OWASP ZAP additionally reported a missing `Cross-Origin-Resource-Policy` (rule 90004).
**Fix:** headers added in `SecurityConfig` for every response, including 401/404 error responses.
**Regression tests:** `SecurityIntegrationTest#securityHeadersOnPublicAndErrorResponses`,
`InputAndExposureTest#securityHeaders`; ZAP rules 10020, 10021 and 10038 are configured to fail the build.

---

## DEF-007
**Product catalog has no pagination**

| Field | Value |
|---|---|
| Severity / Priority | Medium / P2 |
| Component | `GET /api/products` |
| Status | Closed - fixed on branch `fix/def-007-pagination` (tests first, then fix) |
| Found by | k6 load test report (latency per endpoint) |

**Evidence (load test, 50 users, 8 minutes, Mac):** `GET /api/products` was the slowest read:
p95 22 ms, max 95 ms, while product page, search and reviews stayed at 8-10 ms.

**Root cause:** the endpoint returns **every** active product in one response. The list (and the
response size) grows with each product; in the test environment it grows with every test run.
With a real catalog of thousands of products the page would get slow and expensive.

**Fix (backward compatible):** `GET /api/products?page=0&size=20&sort=id|name|price_asc|price_desc`,
size 1..100 (default 20), every sort ends with `id` so pages are stable. The body **stays a JSON array**
(old clients keep working); the paging information is in headers: `X-Total-Count`, `X-Total-Pages`,
`X-Page`, `X-Page-Size` and an RFC 8288 `Link` header (`rel="next"` / `rel="prev"`), like the GitHub API.
Storefront: "Showing 20 of N products" and a **Load more** button.

**How it was fixed - test first:**
1. Commit 1 added the tests only. CI went **red** with 12 failures (no headers, size 0/101 accepted,
   no sorting) and k6 failed its `catalog_bytes` threshold - proof that the tests detect the defect.
2. Commit 2 added the fix. CI went **green**.

**Evidence - same CI environment, after the whole suite has filled the catalog (k6 `catalog.js`):**

| | Before | After |
|---|---|---|
| Products per response | 234 (whole catalog) | 20 |
| Response size p95 | 32,927 bytes | 2,998 bytes (**-91 %**) |
| Response size growth | grows with every product | constant |
| Latency p95 (CI) | 6.1 ms | 7.2-7.5 ms |

Latency on CI did not change measurably: with only 234 products the JSON is small either way, and
the paginated query adds a `COUNT(*)` for the total. On the **larger local database** the latency gain
is clear - same machine, same k6 `load.js` (40 browsing + 10 buying users, 8 minutes), before and after:

| `GET /api/products` (MacBook) | Before (00:38 UTC) | After (02:57 UTC) | Change |
|---|---|---|---|
| p95 | 22 ms | 9.0 ms | **-59 %** |
| average | 16 ms | 6.3 ms | -61 % |
| max | 95 ms | 22 ms | -77 % |
| Rank among reads | slowest | in line with the other reads (7-9 ms) | |

Whole run: overall p95 20 -> 16 ms, p99 25 -> 20 ms, purchase journey p95 81 -> 73 ms, 0 % errors in both runs.
Conclusion: on a small catalog the fix bounds the cost; on a large one it also makes the page faster.

**Regression tests:** `CatalogPaginationTest` (bounded default page, all pages exactly once, Link header,
boundaries, sorting, size < 10 KB), `ProductServiceTest#invalidPageSizeOrSortIs400`,
`ProductServiceTest#requestedPageAndStableSortReachTheDatabase`, `ApiIntegrationTest#catalogIsPaginatedWithTotalsAndLinks_DEF007`,
`HomePageUiTest#loadMore`, k6 `performance/tests/catalog.js` (threshold `catalog_bytes p95 < 10 KB`, every CI build).

---

## DEF-008
**UI tests type an extra "a" into every field on Linux**

| Field | Value |
|---|---|
| Severity / Priority | High / P1 for the test suite - every UI test that typed text was invalid in CI |
| Component | `BasePage.type()` (Selenium page objects) |
| Environment | GitHub Actions, Ubuntu, Chrome headless. **Not reproducible on macOS.** |

**Symptom:** in CI, `UiShoppingJourneyTest` failed with "header shows the new customer: expected true
but was false" and `UiAuthTest` timed out after login. On the Mac both passed.
**Investigation:** registration succeeded but the header showed a different e-mail, and the later
login was rejected - so the typed text, not the application, was wrong. Reading `type()` showed it
sends two "select all" shortcuts.
**Root cause:** `type()` cleared a field with `Ctrl+A` **and** `Cmd+A`. On Linux Chrome does not treat
Meta+A as a shortcut and typed a literal "a", so the registered e-mail became `aqa-...@...`.
**Fix:** use `Cmd` on macOS and `Ctrl` elsewhere, and verify after typing that the field holds exactly
the intended value (fails at once with a clear message instead of later with a confusing one).
**Lesson:** run the suite on the CI operating system early; a green local run proves only one platform.

---

## DEF-009
**Flaky "stale element reference" in the purchase journey**

| Field | Value |
|---|---|
| Severity / Priority | Medium / P2 - intermittent red builds erode trust in the suite |
| Component | `OrderPage`, `BasePage` |

**Symptom:** `UiShoppingJourneyTest#endToEndPurchase` failed once in CI with
`StaleElementReferenceException`; the next run passed.
**Root cause:** React re-renders the order page after paying (status, payments list, messages).
The test found an element, React replaced it, then the test read the old reference.
**Fix (root cause, not a retry):** every explicit wait ignores stale references and looks the element
up again; `text()` finds and reads in one retried step; lists are read in one step; "snapshot" waits
compare the page state before and after an action.
**Verification:** the UI suite has been green in every CI run since the fix.

---

## DEF-010
**`Retry-After` says 899 seconds, but the lock lasts 900**

| Field | Value |
|---|---|
| Severity / Priority | Low / P3 - a client waiting exactly `Retry-After` seconds could still be locked |

**Found by:** `SecurityIntegrationTest` expected `900` and got `899` in CI.
**Root cause:** a few milliseconds pass between locking and answering; seconds were rounded **down**.
**Fix:** round up. **Regression test:** `LoginAttemptServiceTest#retryAfterIsRoundedUpSoWaitingThatLongIsEnough`
(299.7 s left -> header 300).

---

## DEF-011
**Cucumber run fails with DuplicateStepDefinitionException**

| Field | Value |
|---|---|
| Severity / Priority | Medium / P2 - the whole BDD suite could not start |

**Root cause:** the same step text was declared twice (`@Given` and `@When` on one method).
Cucumber matches step text and ignores the Given/When/Then keyword, so it counted two definitions.
**Fix:** one annotation per step text; duplicate step texts are now checked (grep over `@Given/@When/@Then`) before a commit.

---

## DEF-012
**`-Dgroups=ui` run fails: Cucumber suite "did not discover any tests"**

| Field | Value |
|---|---|
| Severity / Priority | Low / P3 - all 11 UI tests passed, but the build was red |

**Root cause:** with a tag filter that matches no scenario, the JUnit suite engine reports an empty
suite as an error.
**Fix:** `@Suite(failIfNoTests = false)` on the Cucumber runner.

---

## DEF-013
**"Paid order cannot be paid again" test passed even when the card was charged**

| Field | Value |
|---|---|
| Severity / Priority | High / P1 - the test protecting "no money moves before the 409" protected nothing |
| Component | `OrderServiceTest#paidOrderCannotBePaidAgain` (test code); `OrderService.pay` |
| Found by | PIT mutation testing: the mutant "removed call to `assertCanMoveTo(PAID)`" in `OrderService.pay` **survived** |

**Steps:** remove the line `order.assertCanMoveTo(OrderStatus.PAID)` from `OrderService.pay` and run the unit tests.
**Expected:** a test fails, because now the card is charged first and only then `markPaid()` throws 409.
**Actual:** all tests green.
**Root cause:** the test checked `verify(payments, never()).charge(anyLong(), any(), any())`. In Mockito 5,
`anyLong()` does **not** match `null`, and the order in the test had no id. So the call `charge(null, ...)` did
not match and "never" was always true - a vacuous assertion.
**Fix:** `any()` (matches null). Same rule applied to every `verify(..., never())` in the backend tests.
Proved locally: with the guard removed, the corrected test fails; with the guard in place it passes.
**Lesson:** a negative assertion ("never called") can pass for the wrong reason. Mutation testing is how
you find assertions that cannot fail.

---

## DEF-014
**Jenkins on macOS: `docker: command not found`**

| Field | Value |
|---|---|
| Severity / Priority | Medium / P2 - Jenkins builds #1-#3 red, product unaffected |
| Environment | Jenkins LTS (Homebrew service) on macOS, Docker Desktop installed per user |

**Root cause:** Docker Desktop puts its CLI in `~/.docker/bin`. The Terminal finds it, but Jenkins runs as a
service with a minimal `PATH`. **Fix:** `PATH` in the Jenkinsfile includes `~/.docker/bin` and Docker.app's bin
folder, and a new first stage "Tools on the agent" fails in seconds with the name of any missing tool.

---

## DEF-015
**OWASP ZAP crashes on Apple Silicon (DOM XSS rule starts Firefox)**

| Field | Value |
|---|---|
| Severity / Priority | Medium / P2 - Jenkins builds #4-#6 red after ~9 minutes, no ZAP report |
| Environment | `ghcr.io/zaproxy/zaproxy:stable` on Docker Desktop, Mac ARM (aarch64); GitHub Actions (x86) was green |

**Root cause:** rule 40026 "DOM Based XSS" starts headless Firefox inside the ZAP container. On ARM the browser
start timed out repeatedly, and the ZAP daemon died (`Connection refused`, exit 3).
**Fix:** rule 40026 set to `IGNORE` in `security/zap/zap-rules.tsv` with the reason: this API returns JSON, there
is no DOM. Verified in build #7 that the other active rules really ran (SQL injection, XSS reflected/persistent,
path traversal, command injection, XXE all PASS over 380 URLs), so the gate was not weakened.

---

## DEF-016
**Storefront search shows results of an older search when typing fast**

| Field | Value |
|---|---|
| Severity / Priority | Medium / P2 - a customer searching "mouse" may see keyboards |
| Component | `frontend/src/pages/Home.jsx` |
| Found by | Selenium `HomePageUiTest.searchFilters` (timed out waiting for only matching cards) |

**Root cause:** every keystroke sends `GET /api/products?q=...`; responses can arrive out of order, and the
last one to arrive won, even when it answered an older text ("mo"). The timing change of the upgrade made it visible.
**Fix:** only the newest search may update the list (a guard flag per request, cleared by the effect cleanup)
plus a 200 ms debounce. **Regression test:** `searchFilters` (types the word in one go, like a fast user).

---

## DEF-017
**Query string with a parameter without a name returns 500**

| Field | Value |
|---|---|
| Severity / Priority | Medium / P2 - a client error reported as a server fault; noise in monitoring, possible probe target |
| Component | `GlobalExceptionHandler`; Tomcat 11 parameter parsing |
| Found by | OWASP ZAP rule 100000 after the Spring Boot 4 upgrade; reproduced by an API test against the real server |

**Steps:** `GET /api/products?=x` (or `?q=q&page=0&size=20&=`). **Expected:** 400. **Actual:** 500 "Unexpected server error".
**Root cause:** Tomcat 11 throws `InvalidParameterException` (an `IllegalStateException`) from `getParameter()`
for a malformed query string; it reached the catch-all handler. MockMvc does not use Tomcat, so a MockMvc test passed.
**Fix:** the handler maps `InvalidParameterException` (also when wrapped) to its 4xx code, default 400.
**Regression tests:** `InputAndExposureTest.malformedQueryStrings` (JDK HTTP client, real server; REST Assured
cannot send a nameless parameter), `GlobalExceptionHandlerTest`. CI now shows every backend 500 with its exception.

---

## DEF-018
**Storefront fails WCAG 2.1 AA on every page (contrast, labels, page language)**

| Field | Value |
|---|---|
| Severity / Priority | High / P1 - customers using screen readers or with low vision cannot complete a purchase reliably; legal exposure (ADA) |
| Component | `frontend/index.html`, `frontend/src/style.css`, Home, Cart, ProductDetail, OrderDetail, Wishlist pages |
| Found by | `AccessibilityTest#wcag21aa` (axe-core 4.11 in Chrome), written before the fix: 8 of 8 pages failed |

**Findings:** no `lang` on `<html>` (all pages, serious); white text on the orange buttons 2.9:1, muted text and
rating stars below 4.5:1 (serious); quantity inputs and two selects without an accessible name (critical);
empty table header for the action column (minor).
**Fix:** `lang="en"`; accent `#b45309`, muted `#56616e`, stars `#8a5a00`; `aria-label` on quantity inputs, search,
review sort and return reason; screen-reader-only column headers; visible `:focus-visible` outline.
**Regression test:** `AccessibilityTest#wcag21aa` on 8 pages - 0 serious/critical after the fix.
Manual keyboard and screen-reader checks stay on the release checklist (automated rules find only part of the problems).

---

## DEF-019
**Edge does not start in CI ("Chrome instance exited"), Firefox fine**

| Field | Value |
|---|---|
| Severity / Priority | Medium / P2 - 20 of 20 Edge tests errored before the first page; no product impact |
| Environment | GitHub Actions `ubuntu-latest` (Ubuntu 24.04), Microsoft Edge 154, Selenium 4.25 |

**Root cause (most likely):** Ubuntu 24.04 restricts unprivileged user namespaces through AppArmor; Chrome ships an
AppArmor profile, Edge does not, so Edge's sandbox cannot start and the browser exits at once.
**Fix:** Edge gets `--no-sandbox --disable-dev-shm-usage` only when the `CI` variable is set (CI runners are
throw-away machines); developer machines keep the sandbox. **Result:** Edge 20/20, Firefox 20/20.
**Follow-up (flaky, found before the merge):** one later Edge run timed out in `setCurrentWindowSize` - the
resize of the running headless window added in the same branch. The viewport is now set by start arguments
(`--window-size`, Firefox `--width/--height`); a resize after start is left only for Safari.

---

## DEF-020
**Edge and Firefox UI tests cannot get a driver on the Jenkins Mac (Selenium Manager)**

| Field | Value |
|---|---|
| Severity / Priority | Medium / P2 - 20 of 20 UI tests (incl. 8 accessibility pages) broken before the first page; no product impact |
| Environment | Jenkins builds #14 (`BROWSER=firefox`) and #15 (`BROWSER=edge`), macOS 26 on Apple Silicon, Java 26, Selenium 4.25 |

**Evidence:** `NoSuchDriverException: Unable to obtain: msedgedriver ... error sending request for url
(https://msedgedriver.azureedge.net/LATEST_RELEASE_155_MACOS)` and `Unable to obtain: geckodriver ... cpio archive
error: bad magic value encountered`.
**Root cause:**
- Edge: Microsoft retired the old Azure download host `msedgedriver.azureedge.net`; Selenium Manager in 4.25
  still asks there. Newer Selenium Manager uses `msedgedriver.microsoft.com`.
- Firefox: Firefox is not installed on the Mac, so Selenium Manager tries to download the browser itself and
  fails to unpack the current macOS installer package.
- GitHub CI stayed green because its runners have Edge, Firefox and their drivers preinstalled - the gap only
  shows on a machine where Selenium Manager has to download things.
**Fix:** Selenium 4.25 -> 4.49 (current Selenium Manager); Firefox installed on the Jenkins Mac
(`brew install --cask firefox`).
**Lesson:** a pinned test library also pins its download URLs - external services change, so the browser
tooling needs the same update cadence as the application dependencies.

---

## DEF-021
**Dependency scan silently missed 5 vulnerable libraries (one CVSS 9.1) when Maven Central rate-limited it**

| Field | Value |
|---|---|
| Severity / Priority | High / P1 - a security gate reported "0 vulnerable packages" while a critical one was on the classpath; no product impact (test-scope libraries) |
| Environment | GitHub Actions, OSV-Scanner 2.3.0 (`--recursive` over `pom.xml` and `package-lock.json`) |

**How it was found:** the new runner canary (whole pipeline on `ubuntu-26.04`) reported 5 vulnerable packages
in `automation/pom.xml`; the normal run on the same commit, minutes earlier, reported 0. A gate that gives two
answers for one commit is itself a defect.
**Evidence (diagnosis runs on both images, same commit):**
- OSV log: `Maven registry query status: 429` for BOM/parent POMs, then `failed to resolve transitive
  dependencies ... falling back to offline extraction`.
- Packages seen in `automation/pom.xml`: **14** after the fallback vs. **130** when resolution worked
  (backend: 16 vs. 144). Which run hit the rate limit was random, not tied to the Ubuntu version.
- `mvn dependency:tree` confirmed all five on the test classpath: freemarker 2.3.33 (CVSS 9.1, via
  allure-rest-assured), rhino 1.7.7.2 (via json-schema-validator), httpclient5 / httpcore5 / httpcore5-h2
  (via axe-core -> WebDriverManager).

**Root cause:** OSV-Scanner resolves Maven transitive dependencies itself; on HTTP 429 it logs a warning,
scans only the direct dependencies and still ends normally. The gate trusted a partial input.
**Fix:**
- `security/osv-scan.sh` (GitHub Actions and Jenkins): Maven resolves the full tree and writes a CycloneDX SBOM
  per module (test scope included); OSV scans only these SBOMs and the npm lockfile.
- Completeness check (an SBOM with fewer than 50 components fails the job) and a self-test (a fixture SBOM with
  log4j-core 2.14.1 must be reported, otherwise the vulnerability database was not reachable).
- Libraries: freemarker 2.3.35, rhino 1.7.15.1, WebDriverManager excluded (unused - Selenium Manager provides drivers).

**Result:** 0 vulnerable packages from complete inputs (backend 144, automation 116 components); contract,
accessibility and all other tests green on both runner images.
**Lesson:** a security gate needs the same checks as a test: complete input, and proof that it can fail.

---

## DEF-022
**Jenkins QA Reports page said "all gates passed" for a build that failed in its first stage**

| Field | Value |
|---|---|
| Severity / Priority | High / P1 - a quality report contradicted the build result; anyone reading only the report would release |
| Environment | Jenkins on macOS, build #30 (JDK installation misconfigured, pipeline stopped in "Tools on the agent") |

**Evidence:** build #30: `Finished: FAILURE`, every stage after "Tools on the agent" skipped - yet the QA Reports page
showed 284/284, 287/287, coverage, PIT, k6, ZAP and OSV, all green, under "overall: all gates passed".
**Root cause:**
- The dashboard reads report files from the Jenkins workspace. Nothing removed the files of the previous
  successful run, so a build that ran no test at all presented the old results as its own.
- The headline only looked at the cards, never at the pipeline result; with no cards at all it also said "passed".

**Fix:**
- "Tools on the agent" first deletes old outputs (`qa-reports`, `*/target`, k6, ZAP and OSV results).
- `PIPELINE_RESULT` (Jenkins `currentBuild.currentResult`) goes into the dashboard: a non-successful pipeline
  is red with "pipeline failure - see Console Output", and a page without any passed gate says "no gate ran".

**Lesson:** a report is a test result too - it needs a defined state for "nothing ran", and it must never be
greener than the build it belongs to.

---

## Report template

```
ID / Title
Severity (Critical/High/Medium/Low) / Priority (P1-P3) / Component / Environment / Build
Steps to reproduce (numbered, with exact data)
Expected result / Actual result
Evidence (status codes, response body, screenshot, Allure link, k6 report)
Root cause
Fix
Regression test(s)
Status / Lessons learned
```
