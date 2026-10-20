import { test, expect } from '../support/fixtures';

/** The startup banner of the storefront (REQ-ADM-01): picture, large watermark and a way in for visitors. */
test('a visitor sees the welcome banner with its picture, watermark and log-in buttons @mobile', async ({ page }) => {
  await page.goto('/#/');
  const banner = page.getByTestId('banner');
  await expect(banner).toBeVisible();
  await expect(banner.getByTestId('banner-code')).toHaveText('WELCOME10');
  await expect(banner.getByTestId('banner-art')).toBeVisible();
  await expect(banner.getByTestId('watermark')).toHaveCount(1);
  await expect(banner.getByTestId('banner-register')).toHaveAttribute('href', '#/register');

  await banner.getByTestId('banner-login').click();
  await expect(page.getByTestId('auth-card')).toBeVisible();
});

test('a signed-in customer gets the banner without the log-in buttons', async ({ signedIn: page }) => {
  await page.goto('/#/');
  await expect(page.getByTestId('banner')).toBeVisible();
  await expect(page.getByTestId('banner-shop')).toBeVisible();
  await expect(page.getByTestId('banner-login')).toHaveCount(0);
  await expect(page.getByTestId('banner-register')).toHaveCount(0);
});
