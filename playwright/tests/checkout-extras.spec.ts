import { test, expect } from '../support/fixtures';
import { ADDRESS, CheckoutPage } from '../pages/checkout-page';

/** Guest checkout (REQ-AUTH-15) and the address book (REQ-ADR-01..03) in the browser. */

test('a guest checks out with only an e-mail and is shown how to keep the orders @mobile', async ({ page, api }) => {
  const product = await api.createProduct({ price: 9, stock: 5 });
  const email = `guest-${Date.now()}-${Math.floor(Math.random() * 1e6)}@test.com`;
  await page.goto('/#/login');

  await page.getByTestId('guest-continue').click();                           // the browser checks the e-mail first
  await expect(page.getByTestId('guest-email-error')).toHaveText('Enter your email address.');
  await page.getByTestId('guest-email').fill(email);
  await page.getByTestId('guest-continue').click();
  await expect(page.getByTestId('nav-user')).toHaveText(email);
  expect(await page.evaluate(() => localStorage.getItem('guest'))).toBe('true');

  const token = await page.evaluate(() => localStorage.getItem('token'));
  await api.addToCart(token!, product.id, 2);
  const checkout = await new CheckoutPage(page).open();
  await expect(page.getByTestId('guest-hint')).toContainText('shopping as a guest');
  await expect(page.getByTestId('save-address')).toHaveCount(0);              // guests have no address book
  await checkout.fillAddress(ADDRESS);
  await checkout.placeOrderButton().click();
  await expect(page).toHaveURL(/#\/orders\/\d+$/);

  // the same e-mail again, in a new browser session: sent to "Forgot password"
  await page.evaluate(() => localStorage.clear());
  await page.goto('/#/login');
  await page.reload();
  await page.getByTestId('guest-email').fill(email);
  await page.getByTestId('guest-continue').click();
  await expect(page.getByTestId('guest-error')).toContainText('Forgot password');
});

test('the address book: add with validation, default first, make default, delete @mobile', async ({ signedIn: page }) => {
  await page.goto('/#/addresses');
  await expect(page.getByTestId('addresses-empty')).toBeVisible();

  await page.getByTestId('address-add').click();
  await page.getByTestId('ab-zip').fill('12');
  await page.getByTestId('ab-save').click();
  await expect(page.getByTestId('ab-fullName-error')).toHaveText('Please fill in this field');
  await expect(page.getByTestId('ab-zip-error')).toHaveText('Use a US ZIP code: 12345 or 12345-6789.');

  const fill = async (label: string, line1: string) => {
    await page.getByTestId('ab-label').fill(label);
    await page.getByTestId('ab-fullName').fill('Ada Lovelace');
    await page.getByTestId('ab-line1').fill(line1);
    await page.getByTestId('ab-city').fill('Pittsburgh');
    await page.getByTestId('ab-state').selectOption('PA');
    await page.getByTestId('ab-zip').fill('15213');
  };
  await fill('Home', '1 Main St');
  await page.getByTestId('ab-save').click();
  await expect(page.getByTestId('address-notice')).toHaveText('Address saved.');
  await expect(page.getByTestId('address-card')).toHaveCount(1);
  await expect(page.getByTestId('address-default')).toHaveCount(1);

  await page.getByTestId('address-add').click();
  await fill('Office', '1 MAIN ST');                                          // the same place: refused by the server
  await page.getByTestId('ab-save').click();
  await expect(page.getByTestId('address-error')).toContainText('already in your address book');
  await page.getByTestId('ab-line1').fill('9 Work Rd');
  await page.getByTestId('ab-save').click();
  await expect(page.getByTestId('address-card')).toHaveCount(2);

  const office = page.getByTestId('address-card').filter({ hasText: 'Office' });
  await office.getByTestId('address-make-default').click();
  await expect(page.getByTestId('address-card').first()).toContainText('Office');   // the default comes first
  await expect(office).toHaveAttribute('data-default', 'true');

  const home = page.getByTestId('address-card').filter({ hasText: 'Home' });
  await home.getByTestId('address-delete').click();
  await home.getByTestId('address-delete-yes').click();
  await expect(page.getByTestId('address-card')).toHaveCount(1);
});

test('checkout starts with the default saved address, another one can be picked, a new one can be saved', async ({ signedIn: page, api, customer }) => {
  const product = await api.createProduct({ price: 12, stock: 9 });
  await api.addToCart(customer.token, product.id, 1);
  for (const [label, line1, makeDefault] of [['Home', '1 Main St', false], ['Office', '9 Work Rd', true]] as const) {
    const r = await page.request.post('/api/addresses', { headers: { Authorization: `Bearer ${customer.token}` },
      data: { label, makeDefault, address: { ...ADDRESS, line1, line2: null } } });
    expect(r.status()).toBe(201);
  }
  const checkout = await new CheckoutPage(page).open();
  await expect(page.getByTestId('saved-address')).toHaveCount(2);
  await expect(page.getByLabel('Street address', { exact: true })).toHaveValue('9 Work Rd');       // the default
  await page.getByTestId('saved-address').filter({ hasText: 'Home' }).click();
  await expect(page.getByLabel('Street address', { exact: true })).toHaveValue('1 Main St');
  await expect(page.getByTestId('save-address')).toHaveCount(0);              // already in the book

  await page.getByTestId('new-address').check();
  await checkout.fillAddress({ ...ADDRESS, line1: '77 New Ave' });
  await page.getByTestId('save-address').check();
  await checkout.placeOrderButton().click();
  await expect(page).toHaveURL(/#\/orders\/\d+$/);

  await page.goto('/#/addresses');
  await expect(page.getByTestId('address-card')).toHaveCount(3);
  await expect(page.getByTestId('address-list')).toContainText('77 New Ave');
});
