import { test, expect, signIn } from '../support/fixtures';
import { Shell } from '../pages/shell';

test.describe('Log out and Exit', () => {
  test('Log out ends the session in the browser and on the server @mobile', async ({ signedIn: page, customer, request }) => {
    const shell = new Shell(page);
    await page.goto('/#/');
    await shell.expectSignedInAs(customer.email);

    const revoked = page.waitForResponse('**/api/auth/logout');
    await shell.logout.click();
    await revoked;
    await shell.expectSignedOut();
    expect(await shell.stored('refreshToken')).toBeNull();

    const reuse = await request.post('/api/auth/refresh', { data: { refreshToken: customer.refreshToken } });
    expect(reuse.status(), 'the old refresh token is worthless now').toBe(401);
  });

  test('Exit logs out, shows the goodbye page and asks the browser to close the window @mobile', async ({ signedIn: page }) => {
    // window.close() of a real test window would end the test: count the calls instead
    await page.addInitScript(() => {
      (window as unknown as { closeCalls: number }).closeCalls = 0;
      window.close = () => { (window as unknown as { closeCalls: number }).closeCalls++; };
    });
    const shell = new Shell(page);
    await page.goto('/#/');
    await shell.exit.click();

    await expect(page).toHaveURL(/#\/goodbye$/);
    await expect(page.getByRole('heading', { name: 'You have left the shop' })).toBeVisible();
    await expect(page.getByTestId('goodbye-signed-out')).toBeVisible();
    expect(await shell.stored('token')).toBeNull();
    await expect.poll(() => page.evaluate(() => (window as unknown as { closeCalls: number }).closeCalls)).toBe(1);

    await page.getByTestId('goodbye-back').click();
    await shell.expectSignedOut();
  });

  test('on a phone Log out and Exit are icons with a name, and also in the menu @mobile', async ({ signedIn: page, customer, isMobile }) => {
    test.skip(!isMobile, 'phone layout');
    const shell = new Shell(page);
    await page.goto('/#/');
    await shell.expectSignedInAs(customer.email);
    await expect(shell.logout).toBeVisible();                       // found by its accessible name ...
    await expect(shell.logout.locator('.btn-label')).toHaveCSS('position', 'absolute');  // ... text only for screen readers

    await shell.menuButton.click();
    await page.getByTestId('side-logout').click();
    await shell.expectSignedOut();
    await shell.menuButton.click();
    await page.getByTestId('side-exit').click();
    await expect(page.getByTestId('goodbye')).toBeVisible();
  });
});

test.describe('Store admin', () => {
  test('admin gives a product a picture: unsafe link stopped with a live preview, safe one saved', async ({ page, api }) => {
    const product = await api.createProduct({ category: 'home' });
    await signIn(page, await api.admin());
    await page.goto('/#/admin/products');
    await page.getByTestId('admin-product-search').fill(product.name);
    const row = page.locator(`[data-testid="admin-product-row"][data-sku="${product.sku}"]`);
    await row.getByTestId('apr-edit').click();

    const field = page.getByTestId('pf-image');
    const preview = page.getByTestId('pf-image-preview');
    await expect(preview).toHaveAttribute('src', '/products/home.svg');         // category picture until one is set

    await field.fill('javascript:alert(1)');
    await page.getByTestId('pf-save').click();
    await expect(page.getByTestId('pf-image-error')).toBeVisible();
    await expect(preview).toHaveAttribute('data-fallback', 'true');            // the bad link is never loaded

    await field.fill('/products/home-mug-002.svg');
    await expect(page.getByTestId('pf-image-error')).toHaveCount(0);
    await expect(preview).toHaveAttribute('src', '/products/home-mug-002.svg');
    await page.getByTestId('pf-save').click();
    await expect(page.getByTestId('admin-notice')).toHaveText('Product updated');
    await expect(row.getByTestId('apr-image')).toHaveAttribute('src', '/products/home-mug-002.svg');
    expect((await api.product(product.id)).imageUrl).toBe('/products/home-mug-002.svg');
  });
});
