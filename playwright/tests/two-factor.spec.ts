import { test, expect } from '../support/fixtures';
import { PASSWORD } from '../support/shop-api';

/** Two-factor sign-in in the browser (REQ-AUTH-16). The e-mailed code itself is checked in the API tests (Mailpit). */

test('a customer turns two-factor on; the next login asks for a code and refuses a wrong one', async ({ signedIn: page, customer }) => {
  await page.goto('/#/security');
  await expect(page.getByTestId('two-factor-state')).toHaveText('Two-factor sign-in is off');
  await page.getByTestId('security-password').fill(PASSWORD);
  await page.getByTestId('two-factor-toggle').click();
  await expect(page.getByTestId('security-ok')).toHaveText('Two-factor sign-in turned on');
  await expect(page.getByTestId('two-factor-state')).toHaveText('Two-factor sign-in is on');

  // a fresh browser context signs in with the password: no session yet, the code page opens
  const fresh = await page.context().browser()!.newContext();
  const login = await fresh.newPage();
  await login.addInitScript(() => localStorage.setItem('lang', 'en'));
  await login.goto('/#/login');
  await login.getByTestId('email-input').fill(customer.email);
  await login.getByTestId('password-input').fill(PASSWORD);
  await login.getByTestId('auth-submit').click();
  await expect(login.getByTestId('code-title')).toHaveText('Enter your sign-in code');
  expect(await login.evaluate(() => localStorage.getItem('token'))).toBeNull();

  await login.getByTestId('code-input').fill('12');
  await login.getByTestId('code-submit').click();
  await expect(login.getByTestId('code-error')).toHaveText('Enter the 6 digits');
  await login.getByTestId('code-input').fill('000000');
  await login.getByTestId('code-submit').click();
  await expect(login.getByTestId('code-error')).toContainText('wrong or has expired');
  expect(await login.evaluate(() => localStorage.getItem('token'))).toBeNull();
  await fresh.close();
});

test('turning it off with a wrong password is refused', async ({ signedIn: page }) => {
  await page.goto('/#/security');
  await page.getByTestId('security-password').fill('Wrong-Password9');
  await page.getByTestId('two-factor-toggle').click();
  await expect(page.getByTestId('security-error')).toBeVisible();
  await expect(page.getByTestId('two-factor-state')).toHaveText('Two-factor sign-in is off');
});
