import { test as base, expect, Page } from '@playwright/test';
import { Session, ShopApi } from './shop-api';

/**
 * Fixtures: what a test needs, built on demand and torn down by Playwright.
 *   api       the shop's API for test data
 *   customer  a brand-new registered customer
 *   signedIn  the browser already logged in as that customer (session put into localStorage before the
 *             app starts, exactly like the storefront keeps it after a real login)
 */
type Fixtures = { api: ShopApi; customer: Session; signedIn: Page };

export async function signIn(page: Page, s: Session) {
  await page.addInitScript(session => {
    localStorage.setItem('lang', 'en');
    localStorage.setItem('token', session.token);
    localStorage.setItem('refreshToken', session.refreshToken);
    localStorage.setItem('email', session.email);
    localStorage.setItem('role', session.role);
  }, s);
}

export const test = base.extend<Fixtures>({
  api: async ({ request }, use) => use(new ShopApi(request)),
  customer: async ({ api }, use) => use(await api.register()),
  signedIn: async ({ page, customer }, use) => {
    await signIn(page, customer);
    await use(page);
  },
  // every test starts in English, whatever the last test chose
  page: async ({ page }, use) => {
    await page.addInitScript(() => { if (!localStorage.getItem('lang')) localStorage.setItem('lang', 'en'); });
    await use(page);
  },
});

export { expect };
