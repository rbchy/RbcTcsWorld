import fs from 'node:fs';
import { test, expect } from '../support/fixtures';
import { ADDRESS } from '../pages/checkout-page';

/** The order invoice as a PDF, downloaded from the order page (REQ-ORD-11). */

test('the order page downloads the invoice as a PDF named after the order @mobile', async ({ signedIn: page, api, customer }) => {
  const product = await api.createProduct({ price: 20, stock: 5 });
  await api.addToCart(customer.token, product.id, 2);
  const r = await page.request.post('/api/orders', { headers: { Authorization: `Bearer ${customer.token}` }, data: { shippingAddress: ADDRESS } });
  expect(r.status()).toBe(201);
  const order = await r.json();

  await page.goto(`/#/orders/${order.id}`);
  const [download] = await Promise.all([page.waitForEvent('download'), page.getByTestId('download-invoice').click()]);
  expect(download.suggestedFilename()).toBe(`invoice-${order.orderNumber}.pdf`);
  const bytes = fs.readFileSync((await download.path())!);
  const text = bytes.toString('latin1');
  expect(text.startsWith('%PDF-1.4')).toBe(true);
  expect(text).toContain(`(Order ${order.orderNumber})`);
  expect(text).toContain('($40.00)');                                          // 2 x $20.00
  await expect(page.getByTestId('order-notice')).toHaveText('Invoice downloaded.');
});
