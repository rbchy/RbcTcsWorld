import { test, expect, signIn } from '../support/fixtures';
import type { APIRequestContext } from '@playwright/test';

/** Partner (affiliate) products such as Amazon links (REQ-AFF-01..03, ADR-0020). */

const LINK = 'https://www.amazon.com/dp/B0PWTEST01?tag=rbctcsworld-20';

async function partnerProduct(request: APIRequestContext, api: any, category = 'shoes') {
  const admin = await api.admin();
  const sku = `PWAFF-${Date.now().toString(36).toUpperCase()}${Math.floor(Math.random() * 99)}`;
  const r = await request.post('/api/products', {
    headers: { Authorization: `Bearer ${admin.token}` },
    data: { name: `PW trail shoe ${sku}`, sku, category, price: 79.99, stock: 3, affiliateUrl: LINK },
  });
  expect(r.status(), await r.text()).toBe(201);
  return r.json();
}

test('a partner product sends the customer to Amazon with the disclosure, never to the cart @mobile', async ({ page, api, request }) => {
  const p = await partnerProduct(request, api);
  await page.goto(`/#/products/${p.id}`);

  const link = page.getByTestId('detail-partner-link');
  await expect(link).toHaveText(/Buy at Amazon/);
  await expect(link).toHaveAttribute('href', LINK);
  await expect(link).toHaveAttribute('target', '_blank');
  await expect(link).toHaveAttribute('rel', /sponsored/);
  await expect(page.getByTestId('partner-disclosure')).toContainText('As an Amazon Associate, RbcTcsWorld Shop earns from qualifying purchases.');
  await expect(page.getByTestId('detail-add-to-cart')).toHaveCount(0);
  await expect(page.getByTestId('detail-price')).toHaveCount(0);           // Amazon prices may not be copied
  await expect(page.getByTestId('detail-image')).toHaveAttribute('src', '/products/shoes.svg');

  const data = JSON.parse(await page.locator('script#ld-json').textContent() ?? '{}');
  expect(data['@type']).toBe('Product');
  expect(data.offers).toBeUndefined();                                     // we neither sell it nor show its price
});

test('the catalog card links to the partner and shows the disclosure above the grid', async ({ page, api, request }) => {
  const p = await partnerProduct(request, api);
  await page.goto('/#/');
  await page.getByTestId('search-input').fill(p.sku);
  const card = page.locator(`[data-testid="product-card"][data-sku="${p.sku}"]`);
  await expect(card.getByTestId('partner-link')).toHaveAttribute('href', LINK);
  await expect(card.getByTestId('add-to-cart')).toHaveCount(0);
  await expect(page.getByTestId('catalog-partner-disclosure')).toBeVisible();
});

test('the admin adds the partner link in the product form; only https links are accepted', async ({ page, api }) => {
  await signIn(page, await api.admin());
  await page.goto('/#/admin/products');
  await page.getByTestId('admin-product-add').click();
  const sku = `PWAF-${Date.now().toString(36).toUpperCase()}`;
  await page.getByTestId('pf-name').fill(`Partner jacket ${sku}`);
  await page.getByTestId('pf-sku').fill(sku);
  await page.getByTestId('pf-category').fill('clothing');
  await page.getByTestId('pf-price').fill('49.00');
  await page.getByTestId('pf-stock').fill('1');
  await page.getByTestId('pf-affiliate').fill('http://www.amazon.com/dp/B0');
  await page.getByTestId('pf-save').click();
  await expect(page.getByTestId('pf-affiliate-error')).toHaveText('Use an https:// link without spaces or quotes.');

  await page.getByTestId('pf-affiliate').fill(LINK);
  await page.getByTestId('pf-save').click();
  await expect(page.getByTestId('admin-notice')).toHaveText('Product saved');
  await page.getByTestId('admin-product-search').fill(`Partner jacket ${sku}`);
  // other tests' partner products are in the list too until the search applies: look in this product's row only
  await expect(page.locator('tr', { hasText: sku }).getByTestId('apr-partner')).toHaveText('Amazon');
});
