import { test, expect, signIn } from '../support/fixtures';

/** A product's shipping weight and box size in the admin form (REQ-FUL-06, ADR-0013). */

test('the admin gives a product its shipping weight and box; a box needs all three sides', async ({ page, api, request }) => {
  await signIn(page, await api.admin());
  await page.goto('/#/admin/products');
  await page.getByTestId('admin-product-add').click();
  const sku = `PWBOX-${Date.now().toString(36).toUpperCase()}`;
  await page.getByTestId('pf-name').fill(`Boxed boot ${sku}`);
  await page.getByTestId('pf-sku').fill(sku);
  await page.getByTestId('pf-category').fill('shoes');
  await page.getByTestId('pf-price').fill('89.00');
  await page.getByTestId('pf-stock').fill('2');
  await page.getByTestId('pf-weight').fill('44.5');
  await page.getByTestId('pf-length').fill('14');
  await page.getByTestId('pf-width').fill('9');
  await page.getByTestId('pf-save').click();
  await expect(page.getByTestId('pf-height-error')).toHaveText('Give length, width and height together, or none.');

  await page.getByTestId('pf-height').fill('5.25');                           // one decimal at most
  await page.getByTestId('pf-save').click();
  await expect(page.getByTestId('pf-height-error')).toHaveText('Enter inches above 0, up to 108, with at most 1 decimal.');

  await page.getByTestId('pf-height').fill('5.5');
  await page.getByTestId('pf-save').click();
  await expect(page.getByTestId('admin-notice')).toHaveText('Product saved');

  const r = await request.get(`/api/products?q=${sku}`);
  const p = (await r.json()).find((x: any) => x.sku === sku);
  expect(p).toMatchObject({ weightOz: 44.5, lengthIn: 14, widthIn: 9, heightIn: 5.5 });

  await page.getByTestId('admin-product-search').fill(sku);
  await page.locator('tr', { hasText: sku }).getByTestId('apr-edit').click();
  await expect(page.getByTestId('pf-weight')).toHaveValue('44.5');
  await expect(page.getByTestId('pf-length')).toHaveValue('14');
  await expect(page.getByTestId('pf-height')).toHaveValue('5.5');
});
