import { test, expect, signIn } from '../support/fixtures';

/**
 * STORE_SALES=off: the site runs as a partner (affiliate) site (REQ-AFF-04, ADR-0026). The backend's side is proven
 * by PartnerOnlyModeIntegrationTest; here the storefront is told "sales: false" and must hide every shop part.
 */
test.beforeEach(async ({ page }) => {
  await page.route('**/api/store/mode', route => route.fulfill({ json: { sales: false } }));
});

test('a partner site shows no cart, coupon banner or shop promises @mobile', async ({ page }) => {
  await page.goto('/#/');
  await expect(page.getByTestId('announce-partner')).toContainText('bought at our partner shops');
  await expect(page.getByTestId('top-cart')).toHaveCount(0);
  await expect(page.getByTestId('banner-code')).toHaveCount(0);
  await expect(page.getByTestId('foot-partner')).toContainText('As an Amazon Associate we earn from qualifying purchases.');
  await expect(page.getByTestId('foot-returns-policy')).toHaveCount(0);
  await expect(page.getByTestId('foot-shipping-policy')).toHaveCount(0);
});

test('the shop pages say the site does not sell its own products', async ({ page, api }) => {
  await signIn(page, await api.register());
  for (const route of ['cart', 'checkout', 'orders', 'payment-methods', 'returns-policy']) {
    await page.goto(`/#/${route}`);
    await expect(page.getByTestId('not-selling')).toContainText('This site does not sell its own products yet.');
  }
  await expect(page.getByTestId('nav-cart')).toHaveCount(0);
  await expect(page.getByTestId('nav-orders')).toHaveCount(0);
});

test('the privacy policy names Amazon as a third party that may set cookies', async ({ page }) => {
  await page.goto('/#/privacy');
  await expect(page.getByTestId('legal-partner-site')).toBeVisible();
  await expect(page.getByTestId('legal-third-party')).toContainText(
    'Third parties, including Amazon, may provide content and advertising on this site, collect information directly from visitors, and place or recognize cookies on visitors\' browsers.');
});
