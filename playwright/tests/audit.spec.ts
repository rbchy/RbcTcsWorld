import { test, expect, signIn } from '../support/fixtures';

/** Audit trail page (REQ-AUDIT-03): the admin finds who changed a product, checks integrity, downloads CSV. */
test.describe('Audit trail', () => {
  test('the admin filters to one product, verifies the chain and downloads the CSV', async ({ page, api }) => {
    const product = await api.createProduct({ price: 12.5, stock: 20 });
    await signIn(page, await api.admin());

    await page.goto('/#/admin/audit');
    await page.getByTestId('audit-type').selectOption('PRODUCT');
    await page.getByTestId('audit-id').fill(String(product.id));
    await page.getByTestId('audit-show').click();

    const rows = page.getByTestId('audit-row');
    await expect(rows).toHaveCount(6);                       // name, sku, category, price, stock, active
    await expect(page.getByTestId('audit-row-field')).toContainText(['PRODUCT_CREATED · active']);
    await expect(page.getByTestId('audit-row-new').filter({ hasText: product.sku })).toHaveCount(1);

    await page.getByTestId('audit-verify').click();
    await expect(page.getByTestId('audit-check')).toContainText('Intact');

    const [download] = await Promise.all([page.waitForEvent('download'), page.getByTestId('audit-export').click()]);
    expect(download.suggestedFilename()).toBe('audit-trail.csv');
  });

  test('a customer sees the admin-only message, not the trail', async ({ signedIn }) => {
    await signedIn.goto('/#/admin/audit');
    await expect(signedIn.getByTestId('admin-only')).toBeVisible();
    await expect(signedIn.getByTestId('audit-table')).toHaveCount(0);
  });
});
