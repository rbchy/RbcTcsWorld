import { test, expect, signIn } from '../support/fixtures';

/** Flash deals in the browser: badge, struck-through price, countdown, shelf and admin form (REQ-CAT-14). */

const utc = (h: number) => new Date(Date.now() + h * 3600_000).toISOString().slice(0, 19);

async function createDeal(api: any, price: number, dealPrice: number, from: number, to: number) {
  const admin = await api.admin();
  const sku = `PWD-${Date.now().toString(36).toUpperCase()}${Math.floor(Math.random() * 99)}`;
  const r = await api.request.post('/api/products', {
    headers: { Authorization: `Bearer ${admin.token}` },
    data: { name: `PW deal lamp ${sku}`, sku, category: 'qa', price, stock: 9, dealPrice, dealStartsAt: utc(from), dealEndsAt: utc(to) },
  });
  expect(r.status(), await r.text()).toBe(201);
  return r.json();
}

/**
 * The shelf shows at most 12 deals, the one ending first on top. On a database that keeps earlier runs' deals (a local
 * `make app` used all evening), a deal ending in hours is pushed off the shelf: this one ends in 20 minutes, sooner than
 * any deal another run left behind.
 */
const ENDS_SOON = 20 / 60;

test('a running deal shows the sale price, the old price, the percent off and a countdown', async ({ signedIn: page, api }) => {
  const p = await createDeal(api, 40, 25, -1, ENDS_SOON);
  await page.goto(`/#/products/${p.id}`);
  await expect(page.getByTestId('detail-price')).toHaveText('$25.00');
  await expect(page.getByTestId('deal-was')).toHaveText('$40.00');
  await expect(page.getByTestId('deal-badge')).toHaveText('-38%');
  await expect(page.getByTestId('detail-countdown')).toContainText('Ends in');

  await page.goto('/#/');
  const shelf = page.getByTestId('deals-shelf');
  await expect(shelf).toBeVisible();
  await expect(shelf.getByTestId('deal-card').filter({ hasText: p.sku })).toHaveCount(1);
});

test('a deal that has not started shows the normal price', async ({ page, api }) => {
  const p = await createDeal(api, 40, 25, 3, 6);
  await page.goto(`/#/products/${p.id}`);
  await expect(page.getByTestId('detail-price')).toHaveText('$40.00');
  await expect(page.getByTestId('deal-badge')).toHaveCount(0);
});

test('the admin form refuses a deal price that is not lower than the price', async ({ page, api }) => {
  await signIn(page, await api.admin());
  await page.goto('/#/admin/products');
  await page.getByTestId('admin-product-add').click();
  await page.getByTestId('pf-name').fill('Deal form lamp');
  await page.getByTestId('pf-sku').fill(`DF-${Date.now().toString(36).toUpperCase()}`);
  await page.getByTestId('pf-price').fill('10.00');
  await page.getByTestId('pf-stock').fill('3');
  await page.getByTestId('pf-deal-price').fill('12.00');
  await page.getByTestId('pf-deal-start').fill(utc(0).slice(0, 16));
  await page.getByTestId('pf-deal-end').fill(utc(2).slice(0, 16));
  await page.getByRole('button', { name: /save/i }).click();
  await expect(page.getByText('The deal price must be lower than the price')).toBeVisible();
});
