import { test, expect, signIn } from '../support/fixtures';

/** The page frame: coloured menu with icons, cart badge, footer, calendar date fields, dashboard charts (REQ-UI-01..05). */
const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

test('every page ends with the footer; Back to top scrolls up @mobile', async ({ page }) => {
  await page.goto('/#/');
  await expect(page.getByTestId('product-card').first()).toBeVisible();
  const footer = page.getByRole('contentinfo');
  await expect(footer).toContainText('Free shipping on orders of $50 or more');
  await expect(footer).toContainText('30-day returns');
  await footer.scrollIntoViewIfNeeded();
  await page.getByTestId('footer-top').click();
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(0);
  await page.getByTestId('foot-help').click();
  await expect(page).toHaveURL(/#\/help$/);
});

test('every menu entry has an icon tile and the open page is marked', async ({ page }) => {
  await page.goto('/#/');
  for (const id of ['nav-catalog', 'nav-wishlist', 'nav-cart', 'nav-orders', 'side-help']) {
    await expect(page.getByTestId(id).locator('.tile svg')).toHaveCount(1);
  }
  await expect(page.getByTestId('nav-catalog')).toHaveAttribute('aria-current', 'page');
});

test('the cart icon in the top bar counts the items in the cart @mobile', async ({ signedIn: page, api, customer }) => {
  const product = await api.createProduct({ stock: 20 });
  await api.addToCart(customer.token, product.id, 2);
  await page.goto('/#/');
  await expect(page.getByTestId('top-cart-count')).toHaveText('2');
  await expect(page.getByTestId('top-cart')).toHaveAccessibleName('Cart, 2 items');

  await page.goto('/#/cart');
  await page.getByTestId('line-remove').first().click();
  await expect(page.getByTestId('top-cart-count')).toHaveCount(0);   // empty cart: no badge
});

test('Search, Reset and Save buttons carry their icon, and error messages an alert sign', async ({ page, api }) => {
  await signIn(page, await api.admin());
  await page.goto('/#/admin/payments');
  const before = (id: string) => page.getByTestId(id).evaluate(el => {
    const s = getComputedStyle(el, '::before');
    const mask = [s.getPropertyValue('mask-image'), s.getPropertyValue('-webkit-mask-image')];
    return s.content !== 'none' && mask.some(m => m.startsWith('url('));
  });
  expect(await before('py-search')).toBe(true);
  expect(await before('py-reset')).toBe(true);
  await page.getByTestId('py-from').fill('2026-03-01');
  await page.getByTestId('py-to').fill('2026-02-01');
  await page.getByTestId('py-search').click();
  await expect(page.getByTestId('py-to-error')).toBeVisible();
  expect(await before('py-to-error')).toBe(true);
});

test.describe('Calendar date fields', () => {
  test.beforeEach(async ({ page, api }) => { await signIn(page, await api.admin()); });

  test('a day picked in the calendar fills the field; the keyboard moves through the month', async ({ page }) => {
    await page.goto('/#/admin/reports');
    const from = page.getByTestId('rp-from');
    await from.fill('2026-03-10');
    await page.getByTestId('rp-from-cal').click();
    const cal = page.getByTestId('rp-from-calendar');
    await expect(cal).toBeVisible();
    await expect(page.getByTestId('cal-month')).toHaveText('March 2026');
    await expect(page.getByTestId('cal-day-2026-03-10')).toHaveAttribute('aria-pressed', 'true');

    await page.getByTestId('cal-day-2026-03-14').click();
    await expect(cal).toBeHidden();
    await expect(from).toHaveValue('2026-03-14');

    await page.getByTestId('rp-from-cal').click();                    // keyboard: right arrow, one week down, Enter
    await expect(page.getByTestId('cal-day-2026-03-14')).toBeFocused();
    await page.keyboard.press('ArrowRight');
    await page.keyboard.press('ArrowDown');
    await expect(page.getByTestId('cal-day-2026-03-22')).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(from).toHaveValue('2026-03-22');

    await page.getByTestId('rp-from-cal').click();                    // PageDown = next month, Esc closes
    await page.keyboard.press('PageDown');
    await expect(page.getByTestId('cal-month')).toHaveText('April 2026');
    await page.keyboard.press('Escape');
    await expect(cal).toBeHidden();
    await expect(from).toHaveValue('2026-03-22');
  });

  test('Today picks today; the coupon end date keeps its end-of-day time', async ({ page }) => {
    await page.goto('/#/admin/payments');
    await page.getByTestId('py-to-cal').click();
    await page.getByTestId('cal-today').click();
    await expect(page.getByTestId('py-to')).toHaveValue(iso(new Date()));

    await page.goto('/#/admin/coupons');
    await page.getByTestId('cf-until-cal').click();
    const day = new Date(); day.setDate(day.getDate() + 1);
    await page.getByTestId('cal-next-month').click();
    await page.getByTestId('cal-prev-month').click();
    await page.getByTestId(`cal-day-${iso(day)}`).click();
    await expect(page.getByTestId('cf-until')).toHaveValue(`${iso(day)}T23:59`);
  });
});

test('the dashboard shows money per day, orders by status and this month as a calendar', async ({ page, api }) => {
  await signIn(page, await api.admin());
  await page.goto('/#/admin');
  await expect(page.getByTestId('chart-sales').locator('rect')).toHaveCount(14);
  const legend = page.getByTestId('chart-status').locator('li');
  await expect(legend).toHaveCount(7);
  const counts = await legend.locator('strong').allTextContents();
  const total = counts.reduce((s, n) => s + Number(n), 0);
  await expect(page.getByTestId('chart-status').locator('.donut-total')).toHaveText(String(total));
  await expect(page.getByTestId('stat-orders').locator('strong')).toHaveText(String(total));
  await expect(page.getByTestId(`heat-${iso(new Date())}`)).toBeVisible();
});
