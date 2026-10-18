import AxeBuilder from '@axe-core/playwright';
import { test, expect } from '../support/fixtures';

/** Faceted search in the browser: check-box filters with a count behind every option (REQ-CAT-11..12). */

test('a category filter narrows the catalog, shows counts and can be cleared @mobile', async ({ page, api }) => {
  const category = `fx${Date.now().toString(36)}`;
  const product = await api.createProduct({ category, price: 30, stock: 4 });
  await page.goto('/#/');
  const option = page.locator(`[data-testid="filter-category"][data-value="${category}"]`);
  await expect(option).toBeVisible();
  await expect(option.locator('xpath=following-sibling::em')).toHaveText('1');

  const before = await page.getByTestId('product-card').count();
  await option.check();
  await expect(page.getByTestId('product-card')).toHaveCount(1);
  await expect(page.getByTestId('product-card')).toHaveAttribute('data-sku', product.sku);
  await expect(page.getByTestId('banner')).toHaveCount(0);                    // a filtered catalog has no banner

  await page.getByTestId('filters-clear').click();
  await expect(option).not.toBeChecked();
  await expect.poll(() => page.getByTestId('product-card').count()).toBeGreaterThanOrEqual(Math.min(before, 2));
  await expect(page.getByTestId('banner')).toBeVisible();
});

test('colour, price and in-stock filters combine; a colour still offers the other colour', async ({ page }) => {
  await page.goto('/#/');
  await page.getByTestId('search-input').fill('Classic Cotton T-Shirt');
  await expect(page.getByTestId('product-card')).toHaveCount(1);
  const grey = page.locator('[data-testid="filter-color"][data-value="Heather Grey"]');
  const navy = page.locator('[data-testid="filter-color"][data-value="Navy"]');
  await expect(grey).toBeVisible();
  await grey.check();
  await expect(page.getByTestId('product-card')).toHaveCount(1);
  await expect(navy).toBeVisible();                                           // the colour group ignores its own choice
  await expect(page.locator('[data-testid="filter-size"][data-value="XL"]')).toBeVisible();

  await page.locator('[data-testid="filter-price"][data-value="under-25"]').check();   // the tee costs 19.99 to 21.99
  await expect(page.getByTestId('product-card')).toHaveCount(1);
  await expect(page.locator('[data-testid="filter-price"][data-value="25-50"]')).toBeDisabled();   // no grey tee costs 25 to 50
  await page.getByTestId('filter-instock').check();
  await expect(page.getByTestId('product-card')).toHaveCount(1);              // Heather Grey S has stock
  await page.getByTestId('filters-clear').click();
  await expect(page.locator('[data-testid="filter-color"][data-value="Heather Grey"]')).not.toBeChecked();
});

test('the filter panel has no serious accessibility problem', async ({ page }) => {
  await page.goto('/#/');
  await expect(page.getByTestId('filter-instock')).toBeVisible();
  const r = await new AxeBuilder({ page }).include('[data-testid="filters"]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(r.violations.filter(v => v.impact === 'serious' || v.impact === 'critical')).toEqual([]);
});
