import { test, expect, signIn } from '../support/fixtures';

/** Contact form and the owner's answers (REQ-CS-01..04, ADR-0018). */

const visitor = () => `pw-contact-${Date.now().toString(36)}${Math.floor(Math.random() * 999)}@test.com`;

test('a visitor writes from the footer link and gets a message number @mobile', async ({ page }) => {
  await page.goto('/#/');
  await page.getByTestId('foot-contact').click();
  await expect(page).toHaveURL(/#\/contact$/);
  await expect(page.getByTestId('contact-page')).toContainText('within 2 business days');

  await page.getByTestId('ct-send').click();                                   // empty: stopped in the browser
  await expect(page.getByTestId('ct-name-error')).toBeVisible();
  await expect(page.getByTestId('ct-email-error')).toBeVisible();
  await expect(page.getByTestId('ct-message-error')).toHaveText('Please write at least 10 characters.');

  await page.getByTestId('ct-name').fill('Grace Hopper');
  await page.getByTestId('ct-email').fill(visitor());
  await page.getByTestId('ct-topic').selectOption('PRODUCT');
  await page.getByTestId('ct-message').fill('Is the travel mug dishwasher safe?');
  await page.getByTestId('ct-send').click();

  await expect(page.getByTestId('contact-sent')).toBeVisible();
  await expect(page.getByTestId('contact-ticket')).toHaveText(/^MSG-[A-Z2-9]{6}$/);
});

test('a logged-in customer does not type an e-mail address: the account answers', async ({ signedIn: page, customer }) => {
  await page.goto('/#/contact');
  await expect(page.getByTestId('ct-account-email')).toContainText(customer.email);
  await expect(page.getByTestId('ct-email')).toHaveCount(0);
  await page.getByTestId('ct-name').fill('Ada');
  await page.getByTestId('ct-order').fill('ORD-12');
  await page.getByTestId('ct-message').fill('My parcel has not arrived yet.');
  await page.getByTestId('ct-send').click();
  await expect(page.getByTestId('contact-ticket')).toHaveText(/^MSG-/);
});

test('the owner answers a message once and closes it', async ({ page, api, request }) => {
  const sent = await (await request.post('/api/contact', {
    data: { name: 'Linus', email: visitor(), topic: 'RETURN', message: 'Can I return an opened box?' },
  })).json();

  await signIn(page, await api.admin());
  await page.goto('/#/admin/messages');
  const card = page.locator(`[data-testid="admin-message"][data-ticket="${sent.ticket}"]`);
  await expect(card).toContainText('Can I return an opened box?');
  await expect(card.getByTestId('am-status')).toHaveText('New');

  await card.getByTestId('am-reply-open').click();
  await card.getByTestId('am-send').click();                                   // empty answer: stopped
  await expect(card.getByTestId('am-answer-error')).toBeVisible();
  await card.getByTestId('am-answer').fill('Yes, within 30 days of delivery.');
  await card.getByTestId('am-send').click();
  await expect(page.getByTestId('admin-notice')).toHaveText(`Answer to ${sent.ticket} sent`);

  await page.getByTestId('admin-message-status').selectOption('ANSWERED');
  const answered = page.locator(`[data-testid="admin-message"][data-ticket="${sent.ticket}"]`);
  await expect(answered.getByTestId('am-reply')).toContainText('Yes, within 30 days of delivery.');
  await expect(answered.getByTestId('am-reply-open')).toHaveCount(0);           // one answer per message
  await answered.getByTestId('am-close').click();
  await expect(page.getByTestId('admin-notice')).toHaveText(`${sent.ticket} closed`);
});
