import { test, expect, signIn } from '../support/fixtures';

/** The store admin's back office in the browser (REQ-ADM-01..06). */
test('the login page carries the RbcTcsWorld banner with its watermark @mobile', async ({ page }) => {
  await page.goto('/#/login');
  await expect(page.getByTestId('login-banner')).toBeVisible();
  await expect(page.getByTestId('login-banner').getByTestId('watermark')).toHaveCount(1);
  await expect(page.getByTestId('auth-card')).toBeVisible();
});

test.describe('Store admin back office', () => {
  test.beforeEach(async ({ page, api }) => { await signIn(page, await api.admin()); });

  test('the dashboard carries the banner and watermark; the admin menu has sub-menus that fold', async ({ page }) => {
    await page.goto('/#/admin');
    await expect(page.getByTestId('dashboard-banner')).toContainText('Store dashboard');
    await expect(page.getByTestId('dashboard-banner').getByTestId('watermark')).toHaveCount(1);
    for (const g of ['overview', 'catalog', 'sales', 'customers', 'control']) {
      await expect(page.getByTestId(`admin-group-${g}`)).toBeVisible();
    }
    await page.getByTestId('admin-group-catalog').locator('summary').click();     // fold the sub-menu
    await expect(page.getByTestId('nav-admin-products')).toBeHidden();
    await page.getByTestId('dash-reports').click();
    await expect(page.getByTestId('report-table')).toBeVisible();
  });

  test('a report runs for a chosen period, validates the dates and downloads as CSV', async ({ page }) => {
    await page.goto('/#/admin/reports');
    await expect(page.getByTestId('report-title')).toHaveText('Sales');
    await page.getByTestId('rp-from').fill('2026-02-01');
    await page.getByTestId('rp-to').fill('2026-01-01');
    await page.getByTestId('report-run').click();
    await expect(page.getByTestId('rp-to-error')).toHaveText('The end must not be before the start.');

    await page.getByTestId('report-name').selectOption('inventory');
    await expect(page.getByTestId('report-title')).toHaveText('Stock value');
    await expect(page.getByTestId('report-totals')).toContainText('Total');
    const [download] = await Promise.all([page.waitForEvent('download'), page.getByTestId('report-export').click()]);
    expect(download.suggestedFilename()).toBe('report-inventory.csv');
  });

  test('a customer is found by e-mail, disabled after a confirmation and enabled again', async ({ page, api }) => {
    const shopper = await api.register();
    await page.goto('/#/admin/customers');
    await page.getByTestId('cu-q').fill(shopper.email);
    await page.getByTestId('cu-search').click();
    await expect(page.getByTestId('customer-row')).toHaveCount(1);
    await page.getByTestId('customer-view').click();
    await page.getByTestId('customer-disable').click();
    await expect(page.getByTestId('customer-confirm')).toContainText(shopper.email);
    await page.getByTestId('customer-disable-yes').click();
    await expect(page.getByTestId('customer-state')).toHaveText('Disabled');
    await page.getByTestId('customer-enable').click();
    await expect(page.getByTestId('customer-state')).toHaveText('Active');
  });

  test('a late, unfiltered coupon list never replaces the search results (DEF-029)', async ({ page }) => {
    const code = `PWL${Date.now().toString(36).toUpperCase()}`;
    await page.goto('/#/admin/coupons');
    await expect(page.getByTestId('coupon-row').first()).toBeVisible();
    // from now on the unfiltered list answers slowly, as it did on the CI runner that found the defect
    await page.route('**/api/admin/coupons', async route => {
      if (route.request().method() === 'GET' && !new URL(route.request().url()).searchParams.get('q')) {
        await new Promise(resolve => setTimeout(resolve, 1500));
      }
      await route.continue();
    });
    await page.getByTestId('cf-code').fill(code);
    await page.getByTestId('cf-value').fill('10');
    await page.getByTestId('cf-save').click();                 // Save reloads the (unfiltered) list: slow
    await page.getByTestId('cs-q').fill(code);
    await page.getByTestId('cs-search').click();               // the filtered answer comes first
    await expect(page.getByTestId('coupon-row')).toHaveCount(1);
    await page.waitForTimeout(2000);                           // the slow, unfiltered answer has arrived by now
    await expect(page.getByTestId('coupon-row')).toHaveCount(1);
    await expect(page.getByTestId('coupon-row')).toContainText(code);
  });

  test('a coupon is edited (its code stays) and switched off', async ({ page }) => {
    const code = `PW${Date.now().toString(36).toUpperCase()}`;
    await page.goto('/#/admin/coupons');
    await page.getByTestId('cf-code').fill(code);
    await page.getByTestId('cf-value').fill('10');
    await page.getByTestId('cf-save').click();
    await page.getByTestId('cs-q').fill(code);
    await page.getByTestId('cs-search').click();
    // the row of this coupon (a late answer of the first, unfiltered load may still show every coupon for a moment)
    const row = page.getByTestId('coupon-row').filter({ hasText: code });
    await expect(row).toHaveCount(1);
    await expect(page.getByTestId('coupon-row')).toHaveCount(1);

    await row.getByTestId('coupon-edit').click();
    await expect(page.getByTestId('cf-code')).toHaveAttribute('readonly', '');
    await page.getByTestId('cf-value').fill('150');
    await page.getByTestId('cf-save').click();
    await expect(page.getByTestId('cf-value-error')).toHaveText('A percent discount must be 100 or less.');
    await page.getByTestId('cf-value').fill('15');
    await page.getByTestId('cf-save').click();
    await expect(page.getByTestId('admin-notice')).toContainText(code);
    await expect(row).toContainText('15%');

    await row.getByTestId('coupon-toggle').click();
    await expect(row.getByTestId('coupon-state')).toHaveText('Switched off');
  });

  test('advanced product search refuses a price range that makes no sense', async ({ page }) => {
    await page.goto('/#/admin/products');
    await page.getByTestId('ps-min').fill('20');
    await page.getByTestId('ps-max').fill('5');
    await page.getByTestId('ps-search').click();
    await expect(page.getByTestId('ps-max-error')).toHaveText('The highest must not be below the lowest.');
    await page.getByTestId('ps-max').fill('500');
    await page.getByTestId('ps-instock').check();
    await page.getByTestId('ps-search').click();
    await expect(page.getByTestId('ps-max-error')).toHaveCount(0);
  });

  test('stock is received, moved between warehouses and refused when a warehouse holds too little', async ({ page, api }) => {
    const product = await api.createProduct({ stock: 4 });              // 4 units in the default warehouse (PIT)
    await page.goto('/#/admin/warehouses');
    await expect(page.getByTestId('warehouse-row').first()).toHaveAttribute('data-code', 'PIT');
    await page.getByTestId('ss-q').fill(product.sku);
    await page.getByTestId('ss-search').click();
    await page.getByTestId('stock-pick').first().click();
    await expect(page.getByTestId('stock-total')).toContainText('4');

    // moving more than PIT holds is stopped in the form
    await page.getByTestId('tr-from').selectOption({ label: 'PIT (4)' });
    await page.getByTestId('tr-qty').fill('5');
    await page.getByTestId('tr-save').click();
    await expect(page.getByTestId('mv-quantity-error')).toHaveText('The warehouse does not hold that many.');

    await page.getByTestId('tr-to').selectOption({ label: 'NWK (0)' });
    await page.getByTestId('tr-qty').fill('3');
    await page.getByTestId('tr-save').click();
    await expect(page.getByTestId('admin-notice')).toHaveText('Stock moved');
    await expect(page.locator('[data-testid="stock-row"][data-code="NWK"] [data-testid="stock-qty"]')).toHaveText('3');

    await page.getByTestId('adj-reason').selectOption('DAMAGED');
    await page.getByTestId('adj-delta').fill('2');
    await page.getByTestId('adj-save').click();
    await expect(page.getByTestId('mv-delta-error')).toHaveText('Damaged units are removed: use a negative number.');
  });
});
