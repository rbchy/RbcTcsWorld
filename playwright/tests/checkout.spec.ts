import { test, expect } from '../support/fixtures';
import { ADDRESS, CheckoutPage } from '../pages/checkout-page';

/** Delivery address at checkout (FR-CHK-07..10), with the API checked behind the page. */
test.describe('Checkout: delivery address', () => {
  test.beforeEach(async ({ api, customer }) => {
    const product = await api.createProduct({ price: 12, stock: 5 });
    await api.addToCart(customer.token, product.id);
  });

  test('empty address: the required fields say so and nothing is sent @mobile', async ({ signedIn: page }) => {
    const checkout = await new CheckoutPage(page).open();
    let sent = false;
    page.on('request', r => { if (r.method() === 'POST' && r.url().endsWith('/api/orders')) sent = true; });

    await checkout.placeOrderButton().click();

    for (const f of ['fullName', 'line1', 'city', 'state', 'zip']) {
      await expect(checkout.fieldError(f)).toHaveText('Please fill in this field');
    }
    await expect(checkout.fieldError('line2')).toHaveCount(0);
    await expect(page.getByLabel('ZIP code', { exact: true })).toHaveAttribute('aria-invalid', 'true');
    expect(sent).toBe(false);
  });

  test('a wrong ZIP code comes back from the API and is shown under the ZIP field', async ({ signedIn: page }) => {
    const checkout = await new CheckoutPage(page).open();
    await checkout.fillAddress({ ...ADDRESS, zip: 'ABCDE' });
    const answer = page.waitForResponse(r => r.url().endsWith('/api/orders') && r.request().method() === 'POST');
    await checkout.placeOrderButton().click();

    expect((await answer).status()).toBe(400);
    await expect(checkout.fieldError('zip')).toHaveText('must be a US ZIP code: 12345 or 12345-6789');
    await expect(checkout.fieldError('fullName')).toHaveCount(0);
  });

  test('the address is sent trimmed, shown on the order, and filled in next time @mobile', async ({ signedIn: page, api, customer }) => {
    const checkout = await new CheckoutPage(page).open();
    await checkout.fillAddress({ ...ADDRESS, line2: '', phone: '' });
    const placed = page.waitForResponse(r => r.url().endsWith('/api/orders') && r.request().method() === 'POST');
    await checkout.placeOrderButton().click();
    const order = await (await placed).json();

    expect(order.shippingAddress).toEqual({ ...ADDRESS, line2: null, phone: null });
    await expect(page.getByTestId('order-ship-to')).toHaveText('Ships to: Ada Lovelace, 12 Main St, Pittsburgh, PA 15213');
    expect((await api.order(customer.token, order.id)).status).toBe('PLACED');

    const p2 = await api.createProduct({ price: 8, stock: 5 });
    await api.addToCart(customer.token, p2.id);
    await new CheckoutPage(page).open();
    await expect(page.getByLabel('Full name', { exact: true })).toHaveValue('Ada Lovelace');
    await expect(page.getByLabel('State', { exact: true })).toHaveValue('PA');
    await expect(page.getByLabel('ZIP code', { exact: true })).toHaveValue('15213');
  });
});

/** Sales tax label (REQ-PAY-11): exact with the flat rate; an estimate with Stripe Tax until the address is known. */
test.describe('Checkout: tax line', () => {
  test.use({ serviceWorkers: 'block' });   // WebKit sends a service-worker page's requests past page.route
  test.beforeEach(async ({ api, customer }) => {
    const product = await api.createProduct({ price: 12, stock: 5 });
    await api.addToCart(customer.token, product.id);
  });

  test('with the default flat rate the tax line is simply "Tax"', async ({ signedIn: page }) => {
    await new CheckoutPage(page).open();
    await expect(page.getByTestId('price-tax-label')).toHaveText('Tax');
    await expect(page.getByTestId('price-tax')).toContainText('0.72');                // 6 % of 12.00
  });

  test('with a tax provider the preview says the tax is an estimate', async ({ signedIn: page }) => {
    await page.route('**/api/checkout/quote', async route => {
      const quote = await (await route.fetch()).json();
      return route.fulfill({ json: { ...quote, taxEstimated: true } });
    });
    await new CheckoutPage(page).open();
    await expect(page.getByTestId('price-tax-label')).toHaveText('Estimated tax (exact tax for your address at order)');
  });
});
