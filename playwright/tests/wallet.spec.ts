import { test, expect } from '../support/fixtures';
import { ADDRESS } from '../pages/checkout-page';

/** Saved cards in the browser (REQ-PAY-08). */

test('a customer saves a card, sees only brand and last 4, makes another the default and removes it', async ({ signedIn: page }) => {
  await page.goto('/#/payment-methods');
  await expect(page.getByTestId('wallet-empty')).toBeVisible();

  const add = async (number: string, makeDefault = false) => {
    await page.getByTestId('card-add').click();
    await page.getByTestId('wc-number').fill(number);
    if (makeDefault) await page.getByTestId('wc-default').check();
    await page.getByTestId('wc-save').click();
    await expect(page.getByTestId('wallet-ok')).toHaveText('Card saved');
  };
  await add('4242 4242 4242 4242');
  await add('5555 5555 5555 4444');

  const cards = page.getByTestId('wallet-card');
  await expect(cards).toHaveCount(2);
  await expect(cards.first()).toContainText('Visa •••• 4242');
  await expect(cards.first().getByTestId('wallet-default')).toBeVisible();
  await expect(page.getByTestId('wallet-list')).not.toContainText('5555 5555 5555 4444');

  await cards.nth(1).getByTestId('wallet-make-default').click();
  await expect(page.getByTestId('wallet-card').first()).toContainText('Mastercard •••• 4444');

  await page.getByTestId('wallet-card').first().getByTestId('wallet-delete').click();
  await page.getByTestId('wallet-confirm-delete').click();
  await expect(page.getByTestId('wallet-card')).toHaveCount(1);
  await expect(page.getByTestId('wallet-card').first()).toContainText('Visa •••• 4242');
});

test('a bad card number is flagged in the form', async ({ signedIn: page }) => {
  await page.goto('/#/payment-methods');
  await page.getByTestId('card-add').click();
  await page.getByTestId('wc-number').fill('1234');
  await page.getByTestId('wc-save').click();
  await expect(page.getByTestId('wc-number-error')).toHaveText('Enter 13 to 19 digits');
});

test('paying with a saved card only asks for the CVV', async ({ signedIn: page, customer, api }) => {
  const product = await api.createProduct({ price: 10, stock: 5 });
  await api.addToCart(customer.token, product.id, 1);
  const order = await (await api.request.post('/api/orders', {
    headers: { Authorization: `Bearer ${customer.token}` },
    data: { shippingAddress: ADDRESS },
  })).json();
  const saved = await api.request.post('/api/payment-methods', {
    headers: { Authorization: `Bearer ${customer.token}` },
    data: { cardNumber: '4242424242424242', expiryMonth: 12, expiryYear: 2035, makeDefault: true },
  });
  expect(saved.status()).toBe(201);

  await page.goto(`/#/orders/${order.id}`);
  await expect(page.getByTestId('saved-card')).toContainText('Visa •••• 4242');
  await expect(page.getByTestId('card-number')).toHaveCount(0);
  await page.getByTestId('card-cvv').fill('123');
  await page.getByTestId('pay-button').click();
  await expect(page.getByTestId('payment-row').first()).toContainText('4242');
});
