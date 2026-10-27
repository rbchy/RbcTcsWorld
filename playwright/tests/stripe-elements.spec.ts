import { test, expect } from '../support/fixtures';
import type { APIRequestContext, Page } from '@playwright/test';
import { ADDRESS } from '../pages/checkout-page';

/**
 * Stripe Elements in the storefront (REQ-PAY-09). CI runs the mock provider, so the browser is told to use Stripe's
 * form (config), the shop's card-payment endpoints answer as with Stripe, and Stripe.js is a small stand-in that
 * records what the storefront asks of it. The backend side of the flow is covered by StripeElementsIntegrationTest.
 */
// In WebKit a page controlled by the store's service worker sends its requests through it, past page.route; the
// stand-ins below must see every request, so this file runs without the service worker.
test.use({ serviceWorkers: 'block' });

const FAKE_STRIPE_JS = `
  window.__stripe = { calls: [], result: {} };
  window.Stripe = function (key) {
    window.__stripe.calls.push(['Stripe', key]);
    return {
      elements(opts) {
        window.__stripe.calls.push(['elements', opts.clientSecret]);
        return { create(type) {
          window.__stripe.calls.push(['create', type]);
          return { mount(el) {
            const frame = document.createElement('div');
            frame.setAttribute('data-testid', 'fake-stripe-frame');
            frame.textContent = 'Card number (Stripe frame)';
            el.appendChild(frame);
          } };
        } };
      },
      async confirmPayment(args) {
        window.__stripe.calls.push(['confirmPayment', args.redirect]);
        return window.__stripe.result;
      },
    };
  };`;

async function placedOrder(request: APIRequestContext, token: string) {
  const r = await request.post('/api/orders', { headers: { Authorization: `Bearer ${token}` }, data: { shippingAddress: ADDRESS } });
  expect(r.status()).toBe(201);
  return (await r.json()) as { id: number; total: number };
}

/** The storefront as it runs with STRIPE_PUBLISHABLE_KEY set; paid = what the confirm call reports. */
async function withStripe(page: Page, orderId: number, confirmStatus: 'PAID' | 'PLACED') {
  const seen = { started: 0, confirmed: 0, cardNumberSent: false };
  let paid = false;
  page.on('request', r => {
    if (/\/api\/orders\/\d+\/pay(-saved)?$/.test(r.url()) || /\d{13,19}/.test(r.postData() ?? '')) seen.cardNumberSent = true;
  });
  await page.route('https://js.stripe.com/v3/', route => route.fulfill({ contentType: 'application/javascript', body: FAKE_STRIPE_JS }));
  await page.route('**/api/payments/config', route =>
    route.fulfill({ json: { provider: 'stripe', cardForm: 'stripe-elements', publishableKey: 'pk_test_pw' } }));
  await page.route(`**/api/orders/${orderId}/card-payment`, route => {
    seen.started++;
    return route.fulfill({ json: { clientSecret: `pi_pw_${orderId}_secret_1`, publishableKey: 'pk_test_pw' } });
  });
  await page.route(`**/api/orders/${orderId}/card-payment/confirm`, async route => {
    seen.confirmed++;
    paid = confirmStatus === 'PAID';
    const order = await (await route.fetch({ url: route.request().url().replace('/card-payment/confirm', ''), method: 'GET' })).json();
    return route.fulfill({ json: { ...order, status: confirmStatus } });
  });
  await page.route(`**/api/orders/${orderId}`, async route => {
    const response = await route.fetch();
    const order = await response.json();
    return route.fulfill({ response, json: paid ? { ...order, status: 'PAID' } : order });
  });
  return seen;
}

test('the card is entered in Stripe\'s form and the order shows as paid once the server confirms it', async ({ signedIn: page, customer, api, request }) => {
  const product = await api.createProduct({ price: 10, stock: 5 });
  await api.addToCart(customer.token, product.id, 1);
  const order = await placedOrder(request, customer.token);
  const seen = await withStripe(page, order.id, 'PAID');

  await page.goto(`/#/orders/${order.id}`);
  await expect(page.getByTestId('stripe-pay-form')).toBeVisible();
  await expect(page.getByTestId('fake-stripe-frame')).toBeVisible();            // Stripe's frame, not our inputs
  await expect(page.getByTestId('card-number')).toHaveCount(0);
  await expect(page.getByTestId('card-cvv')).toHaveCount(0);
  await expect(page.getByTestId('stripe-secure-note')).toContainText('never sees or stores the card number');
  expect(await page.evaluate(() => (window as any).__stripe.calls.slice(0, 3)))
    .toEqual([['Stripe', 'pk_test_pw'], ['elements', `pi_pw_${order.id}_secret_1`], ['create', 'payment']]);

  await page.getByTestId('stripe-pay-button').click();

  await expect(page.getByTestId('order-notice')).toHaveText('Payment received. Thank you!');
  await expect(page.getByTestId('order-status')).toHaveText('PAID');
  await expect(page.getByTestId('stripe-pay-form')).toHaveCount(0);
  expect(await page.evaluate(() => (window as any).__stripe.calls.at(-1))).toEqual(['confirmPayment', 'if_required']);
  expect(seen).toEqual({ started: 1, confirmed: 1, cardNumberSent: false });     // no card number ever sent to the shop
});

test('a card Stripe declines shows Stripe\'s reason and the shop is not asked to confirm', async ({ signedIn: page, customer, api, request }) => {
  const product = await api.createProduct({ price: 10, stock: 5 });
  await api.addToCart(customer.token, product.id, 1);
  const order = await placedOrder(request, customer.token);
  const seen = await withStripe(page, order.id, 'PAID');

  await page.goto(`/#/orders/${order.id}`);
  await expect(page.getByTestId('fake-stripe-frame')).toBeVisible();
  await page.evaluate(() => { (window as any).__stripe.result = { error: { message: 'Your card was declined.' } }; });
  await page.getByTestId('stripe-pay-button').click();

  await expect(page.getByTestId('stripe-pay-error')).toHaveText('Your card was declined.');
  await expect(page.getByTestId('stripe-pay-button')).toBeEnabled();             // the customer can try another card
  await expect(page.getByTestId('order-status')).toHaveText('PLACED');
  expect(seen.confirmed).toBe(0);
});

test('a payment the bank is still processing leaves the order unpaid with a clear message', async ({ signedIn: page, customer, api, request }) => {
  const product = await api.createProduct({ price: 10, stock: 5 });
  await api.addToCart(customer.token, product.id, 1);
  const order = await placedOrder(request, customer.token);
  await withStripe(page, order.id, 'PLACED');

  await page.goto(`/#/orders/${order.id}`);
  await expect(page.getByTestId('fake-stripe-frame')).toBeVisible();
  await page.getByTestId('stripe-pay-button').click();

  await expect(page.getByTestId('stripe-pay-error')).toContainText('still processing');
  await expect(page.getByTestId('order-status')).toHaveText('PLACED');
});

test('with the default setup the shop\'s own card form is shown and Stripe.js is never loaded', async ({ signedIn: page, customer, api, request }) => {
  const product = await api.createProduct({ price: 10, stock: 5 });
  await api.addToCart(customer.token, product.id, 1);
  const order = await placedOrder(request, customer.token);
  let stripeLoaded = false;
  page.on('request', r => { if (r.url().includes('js.stripe.com')) stripeLoaded = true; });

  await page.goto(`/#/orders/${order.id}`);
  await expect(page.getByTestId('card-number')).toBeVisible();
  await expect(page.getByTestId('stripe-pay-form')).toHaveCount(0);
  expect(stripeLoaded).toBe(false);
});
