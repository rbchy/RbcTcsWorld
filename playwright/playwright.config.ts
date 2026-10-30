import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright suite for the storefront, next to the Selenium/Java suite in automation/.
 * Same application, different tool: three browser engines (Chromium, Firefox, WebKit = Safari's engine),
 * two phones, network mocking, visual comparison of the product pictures, traces of every failure.
 *
 *   UI_URL   storefront (default http://localhost:5173); the API is reached through it (/api), like the browser does
 *   ADMIN_EMAIL / ADMIN_PASSWORD   store admin (default: the development account)
 */
const CI = !!process.env.CI;

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,                       // every test makes its own customer and products
  forbidOnly: CI,
  retries: CI ? 1 : 0,                       // a pass on retry is reported as "flaky", not hidden
  workers: CI ? 4 : undefined,
  timeout: 45_000,
  expect: {
    timeout: 10_000,
    // product pictures have no text, so they render the same on every Linux machine; small tolerance for
    // anti-aliasing differences between Chromium builds
    toHaveScreenshot: { maxDiffPixelRatio: 0.01 },
  },
  // one baseline per picture, not per operating system: the visual tests run only in Linux Chromium
  // stops at once with a plain message when the shop is not running or a browser is not installed
  globalSetup: './support/global-setup.ts',
  snapshotPathTemplate: '{testDir}/__screenshots__/{testFileName}/{arg}{ext}',
  reporter: [
    ['list'],
    ...(CI ? [['github'] as ['github']] : []),   // failed and flaky tests as annotations on the GitHub run
    ['html', { open: 'never', outputFolder: 'playwright-report' }],
    ['junit', { outputFile: 'junit.xml' }],
  ],
  use: {
    baseURL: process.env.UI_URL ?? 'http://localhost:5173',
    testIdAttribute: 'data-testid',          // the same hooks the Selenium page objects use
    trace: 'retain-on-failure',              // open with: npx playwright show-trace <zip>
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    locale: 'en-US',
  },
  projects: [
    // SKIP_VISUAL=1 (Jenkins on the Mac): the baselines are Linux Chromium pictures
    { name: 'chromium', use: { ...devices['Desktop Chrome'] }, testIgnore: process.env.SKIP_VISUAL ? /visual\.spec/ : undefined },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] }, testIgnore: /visual\.spec/ },
    { name: 'webkit', use: { ...devices['Desktop Safari'] }, testIgnore: /visual\.spec/ },
    // phones: only the tests tagged @mobile (menu behind a button, icon-only Log out / Exit, touch)
    { name: 'mobile-android', use: { ...devices['Pixel 7'] }, grep: /@mobile/ },
    { name: 'mobile-iphone', use: { ...devices['iPhone 14'] }, grep: /@mobile/ },
  ],
});
