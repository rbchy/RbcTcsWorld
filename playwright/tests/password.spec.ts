import { test, expect } from '../support/fixtures';

/** "Forgot your password?" (REQ-AUTH-12): the page never tells who has an account. */
test.describe('Forgot password', () => {
  test('a customer and a stranger see the same confirmation, and the API answers both alike @mobile', async ({ page, customer }) => {
    const answers: string[] = [];
    page.on('response', async r => {
      if (r.url().endsWith('/api/auth/forgot-password')) answers.push(`${r.status()} ${await r.text()}`);
    });

    for (const email of [customer.email, `nobody-${Date.now()}@test.com`]) {
      await page.goto('/#/login');
      await page.getByTestId('forgot-link').click();
      // Wait for the forgot-password page first: the login page also has two e-mail fields (sign in and guest
      // checkout), so a label locator matched them during the page change and failed in strict mode.
      await expect(page.getByTestId('forgot-card')).toBeVisible();
      await page.getByTestId('forgot-email').fill(email);
      await page.getByTestId('forgot-submit').click();
      await expect(page.getByTestId('forgot-sent')).toContainText('If an account exists for this e-mail');
    }

    await expect.poll(() => answers.length).toBe(2);
    expect(answers[0]).toBe(answers[1]);
    expect(answers[0]).toMatch(/^202 /);
  });

  test('the link page without a token says what is missing', async ({ page }) => {
    await page.goto('/#/reset-password');
    await expect(page.getByTestId('reset-error')).toHaveText('This page needs the link from the e-mail.');
    await expect(page.getByTestId('reset-password')).toHaveCount(0);
  });
});
