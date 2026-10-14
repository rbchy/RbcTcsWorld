# ADR 0008: A Playwright (TypeScript) suite beside the Selenium (Java) suite

- **Status:** Accepted
- **Date:** 2026-10 (PR #23)

## Context
Selenium covers real browsers, Safari on the Mac, Appium phones and the Java/Cucumber world. Some important
checks are slow or impossible with it:
- holding back or faking server answers (the DEF-016 race);
- the WebKit engine on Linux;
- pixel comparison of pictures;
- step-by-step traces of a failure.

## Decision
- `playwright/` holds a TypeScript suite with its own fixtures and page objects, using the same `data-testid`
  hooks.
- **Projects:** Chromium, Firefox, WebKit, Pixel 7 and iPhone 14 (`@mobile` tests).
- **Scope:** network mocking, visual regression of the product pictures, a11y, API, Log out / Exit and the
  shopping journey.
- It is not a copy of the Selenium suite: each tool tests what it is best at. Overlap is limited to the main
  journey.
- The CI gate runs with one retry (flaky runs reported). The HTML report and traces go to GitHub Pages.

## Consequences
- Two languages to maintain. This is accepted, because the market asks for both, and each suite stays small
  where the other is strong.
- The visual baselines are Linux Chromium pictures. Jenkins on the Mac skips them (`SKIP_VISUAL=1`).
