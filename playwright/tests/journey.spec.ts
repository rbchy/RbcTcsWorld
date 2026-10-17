import { test, expect } from '../support/fixtures';
import { CatalogPage } from '../pages/catalog-page';
import { ADDRESS, CheckoutPage } from '../pages/checkout-page';

test.describe('Shopping journey', () => {
  test('customer buys with a coupon: a declined card first, then the test card @mobile', async ({ signedIn: page, api, customer }) => {
    const product = await api.createProduct({ price: 30, stock: 5 });

    await test.step('add 2 to the cart from the catalog', async () => {
      const catalog = await new CatalogPage(page).open();
      await catalog.searchFor(product.name);
      await catalog.addToCart(product.sku, 2);
    });

    await test.step('checkout with WELCOME10', async () => {
      await page.goto('/#/cart');
      await expect(page.getByTestId('cart-subtotal')).toHaveText('$60.00');
      await page.getByTestId('go-checkout').click();
      await page.getByTestId('coupon-input').fill('WELCOME10');
      await page.getByTestId('coupon-apply').click();
      await expect(page.getByTestId('price-discount')).toHaveText('-$6.00');
      await expect(page.getByTestId('price-shipping')).toHaveText('FREE');     // $54 after discount >= $50
    });

    await test.step('delivery address', async () => {
      await new CheckoutPage(page).fillAddress(ADDRESS);
    });

    const total = await page.getByTestId('price-total').textContent();
    const placed = page.waitForResponse(r => r.url().endsWith('/api/orders') && r.request().method() === 'POST');
    await page.getByTestId('place-order').click();
    const order = await (await placed).json();
    await expect(page).toHaveURL(new RegExp(`#/orders/${order.id}$`));

    await test.step('declined card: error shown, order still waits for payment', async () => {
      await page.getByTestId('card-number').fill('4000000000000002');
      await page.getByTestId('card-cvv').fill('123');
      await page.getByTestId('pay-button').click();
      await expect(page.getByTestId('order-error')).toBeVisible();
      await expect(page.getByTestId('order-status')).toHaveText('PLACED');
    });

    await test.step('approved test card: PAID, and the API agrees', async () => {
      await page.getByTestId('card-number').fill('4242424242424242');
      await page.getByTestId('pay-button').click();
      await expect(page.getByTestId('order-status')).toHaveText('PAID');
      const saved = await api.order(customer.token, order.id);
      expect(saved.status).toBe('PAID');
      expect(`$${Number(saved.total).toFixed(2)}`).toBe(total);
    });
  });
});
