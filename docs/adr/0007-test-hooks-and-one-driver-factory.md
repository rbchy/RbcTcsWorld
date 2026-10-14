# ADR 0007: data-testid hooks, page objects, and one driver factory for desktop and phone browsers

- **Status:** Accepted
- **Date:** 2026-10 (Module 0), extended in PR #24

## Context
The UI is tested in six languages, five desktop browsers, two phone browsers and two tools (Selenium,
Playwright). Locators built on visible text or CSS layout broke with every translation or restyle.

## Decision
- Every element a test touches has a `data-testid`. Page objects (`automation/.../pages`) and Playwright
  (`testIdAttribute: 'data-testid'`) use only those, or ARIA roles and names.
- `DriverFactory` creates the browser from `-Dbrowser`: chrome, firefox, edge, safari, **android-chrome**,
  **ios-safari**.
- Phones are reached through an **Appium 3** server with a plain `RemoteWebDriver` and W3C capabilities. Appium
  speaks WebDriver, so every page object runs on the phone unchanged. Phone-specific behaviour (opening the
  menu) lives in the page objects (`clickMenuItem`).
- Waits are explicit, ignore stale elements, and are longer on phones (30 s instead of 10 s).

## Alternatives considered
- **Appium `java-client` (`AndroidDriver` / `IOSDriver`):** needed for native apps, not for mobile web. It would
  pin a second Selenium version on the class path.
- **Separate mobile page objects:** they would double the maintenance for the same pages.

## Consequences
- A translation, a restyle or a new phone layout does not break tests unless the behaviour changes.
- Native-app testing would add `java-client` later, behind the same factory.
