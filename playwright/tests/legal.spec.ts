import { test, expect } from '../support/fixtures';

/**
 * The store's policies, cookie notice and agreement lines (REQ-LEG-01..03). The numbers on the policy pages are read
 * from the API, which reads the shop's own settings, so the test compares page and API.
 */
const POLICIES = [
  { path: 'privacy', title: 'Privacy policy', footer: 'foot-privacy' },
  { path: 'terms', title: 'Terms of service', footer: 'foot-terms' },
  { path: 'returns-policy', title: 'Return and refund policy', footer: 'foot-returns-policy' },
  { path: 'shipping-policy', title: 'Shipping policy', footer: 'foot-shipping-policy' },
];

test('every policy is one click from the footer and says it is a draft until a lawyer has read it @mobile', async ({ page }) => {
  for (const p of POLICIES) {
    await page.goto('/#/');
    await page.getByTestId(p.footer).click();
    await expect(page).toHaveURL(new RegExp(`#/${p.path}$`));
    await expect(page.getByTestId('legal-title')).toHaveText(p.title);
    await expect(page.getByTestId('legal-effective')).toHaveText(/^Effective [A-Z][a-z]+ \d{1,2}, \d{4}$/);
    await expect(page.getByTestId('legal-draft')).toContainText('not yet been reviewed by a lawyer');
    await expect(page.getByTestId('legal-contact')).toHaveText('[contact e-mail address]');   // no business registered yet
  }
});

test('the return and shipping policies quote the numbers the shop really uses', async ({ page, request }) => {
  const info = await (await request.get('/api/store/legal')).json();
  expect(info).toMatchObject({ returnWindowDays: 30, complete: false, reviewed: false });

  await page.goto('/#/returns-policy');
  await expect(page.getByTestId('legal-return-days')).toHaveText(`${info.returnWindowDays} days`);
  await expect(page.getByRole('row', { name: /no longer need it/ })).toContainText('except the shipping fee');

  await page.goto('/#/shipping-policy');
  await expect(page.getByTestId('legal-shipping-fee')).toHaveText(`$${Number(info.shippingFee).toFixed(2)}`);
  await expect(page.getByTestId('legal-free-shipping')).toHaveText(`$${Number(info.freeShippingThreshold).toFixed(2)}`);
});

test('the cookie notice says what is stored, links to the privacy policy, and goes away for good @mobile', async ({ page }) => {
  await page.goto('/#/');
  const notice = page.getByTestId('cookie-notice');
  await expect(notice).toContainText('No advertising or tracking cookies');
  await notice.getByTestId('cookie-more').click();
  await expect(page.getByTestId('legal-title')).toHaveText('Privacy policy');
  await expect(page.getByRole('heading', { name: 'Cookies and storage in your browser' })).toBeVisible();

  await page.getByTestId('cookie-ok').click();
  await expect(notice).toHaveCount(0);
  await page.reload();
  await expect(page.getByTestId('legal-title')).toBeVisible();
  await expect(page.getByTestId('cookie-notice')).toHaveCount(0);
});

test('creating an account names the terms and privacy policy it agrees to', async ({ page }) => {
  await page.goto('/#/register');
  const register = page.getByTestId('agree-register');
  await expect(register.getByRole('link', { name: 'Terms of service' })).toHaveAttribute('href', '#/terms');
  await expect(register.getByRole('link', { name: 'Privacy policy' })).toHaveAttribute('href', '#/privacy');
});

test('placing an order names the terms and the return policy it agrees to', async ({ signedIn: page, api, customer }) => {
  const product = await api.createProduct({ price: 12, stock: 5 });
  await api.addToCart(customer.token, product.id);
  await page.goto('/#/checkout');
  const order = page.getByTestId('agree-order');
  await expect(order.getByRole('link', { name: 'Return and refund policy' })).toHaveAttribute('href', '#/returns-policy');
  await expect(order.getByRole('link', { name: 'Terms of service' })).toHaveAttribute('href', '#/terms');
});

test('in another language the page says the policy is in English', async ({ page }) => {
  await page.goto('/#/terms');
  await expect(page.getByTestId('legal-english')).toHaveCount(0);
  await page.evaluate(() => localStorage.setItem('lang', 'bn'));
  await page.reload();
  await expect(page.getByTestId('legal-english')).toContainText('ইংরেজিতে');
  await expect(page.getByTestId('foot-terms')).toHaveText('ব্যবহারের শর্তাবলি');
  await expect(page.getByTestId('legal-title')).toHaveText('Terms of service');
});
