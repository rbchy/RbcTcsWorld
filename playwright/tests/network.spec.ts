import { test, expect } from '../support/fixtures';
import { CatalogPage } from '../pages/catalog-page';

/** Situations that are hard to create with a real server: Playwright answers the browser's requests itself. */
test.describe('Network: errors and timing', () => {
  test('a server error is shown as a readable message, not a blank page', async ({ page }) => {
    await page.route('**/api/products?**', route =>
      route.fulfill({ status: 500, json: { status: 500, message: 'Database is not reachable' } }));
    await page.goto('/#/');
    await expect(page.getByTestId('error-banner')).toHaveText('Database is not reachable');
  });

  test('server not running: the page says so', async ({ page }) => {
    await page.route('**/api/products?**', route => route.abort('connectionrefused'));
    await page.goto('/#/');
    await expect(page.getByTestId('error-banner')).toContainText('Cannot reach the server');
  });

  test('DEF-016: a late answer for an older search never replaces the newer results', async ({ page }) => {
    const catalog = await new CatalogPage(page).open();
    // the answer for "mo" is held back 2 s and would show a product that does not match "mouse"
    await page.route('**/api/products?**', async route => {
      if (new URL(route.request().url()).searchParams.get('q') === 'mo') {
        await new Promise(r => setTimeout(r, 2000));
        await route.fulfill({ json: [{ id: 999, name: 'Monitor stand (stale answer)', sku: 'STALE-1', category: 'x',
          price: 1, stock: 1, active: true, ratingAverage: 0, ratingCount: 0, imageUrl: null }],
          headers: { 'X-Total-Count': '1' } });
      } else {
        await route.continue();
      }
    });
    await catalog.search.pressSequentially('mo');
    await page.waitForRequest(r => r.url().includes('q=mo&'));
    await catalog.search.pressSequentially('use');
    await expect(catalog.card('ELEC-MOUSE-001')).toBeVisible();
    await page.waitForTimeout(2500);                       // the stale answer has arrived by now ...
    await expect(catalog.card('STALE-1')).toHaveCount(0);  // ... and was ignored
  });

  test('picture server down: the category picture is shown', async ({ page, api }) => {
    const product = await api.createProduct({ category: 'home', imageUrl: 'https://cdn.rbctcsworld.test/lamp.png' });
    // the picture server answers 503 (an aborted request behaved differently in WebKit from run to run)
    await page.route('https://cdn.rbctcsworld.test/**', route => route.fulfill({ status: 503, body: 'down' }));
    const catalog = await new CatalogPage(page).open();
    await catalog.searchFor(product.name);
    expect(await catalog.picture(product.sku)).toMatchObject({ src: '/products/home.svg', fallback: true });
  });
});
