import { test, expect, signIn } from '../support/fixtures';
import { PASSWORD } from '../support/shop-api';

/** A customer deletes their own account from the Security page (REQ-AUTH-17, ADR-0022). */

test('the customer deletes the account with the password and the word DELETE, then cannot sign in @mobile', async ({ page, api, request }) => {
  const customer = await api.register();
  await signIn(page, customer);
  await page.goto('/#/security');
  await page.getByTestId('delete-account-open').click();

  await page.getByTestId('delete-account-password').fill(PASSWORD);
  await page.getByTestId('delete-account-word').fill('please');
  await page.getByTestId('delete-account-confirm').click();
  await expect(page.getByTestId('delete-account-error')).toHaveText('Type DELETE to confirm');

  await page.getByTestId('delete-account-password').fill('Wrong123!');
  await page.getByTestId('delete-account-word').fill('delete');
  await page.getByTestId('delete-account-confirm').click();
  await expect(page.getByTestId('delete-account-error')).toHaveText('That is not your current password');

  await page.getByTestId('delete-account-password').fill(PASSWORD);
  await page.getByTestId('delete-account-confirm').click();
  await expect(page.getByTestId('goodbye-account-deleted')).toHaveText('Your account was deleted. We sent a last e-mail to confirm it.');

  const login = await request.post('/api/auth/login', { data: { email: customer.email, password: PASSWORD } });
  expect(login.status()).toBe(401);
});
