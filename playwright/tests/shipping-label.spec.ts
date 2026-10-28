import { test, expect, signIn } from '../support/fixtures';

/**
 * Shipping labels in the admin's order list (REQ-FUL-04). CI runs simulated shipping (no labels), so the order list
 * and the label answer are what the shop sends with Shippo; the backend side is ShippoShippingIntegrationTest.
 */
test.use({ serviceWorkers: 'block' });   // WebKit sends a service-worker page's requests past page.route

const SHIPPED = {
  id: 987654, orderNumber: 'ORD-20261027-LABEL1', customerId: 7, status: 'SHIPPED', createdAt: '2026-10-27T10:00:00',
  total: 37.79, carrier: 'USPS', trackingNumber: '9400111899223197428490', items: [], totalQuantity: 3,
  shippingAddress: { fullName: 'Ada Lovelace', line1: '12 Main St', line2: null, city: 'Pittsburgh', state: 'PA', zip: '15213', phone: null },
};

test.describe('Admin: shipping label', () => {
  test.beforeEach(async ({ page, api }) => {
    await signIn(page, await api.admin());
    await page.route('**/api/admin/orders', route => route.fulfill({ json: [SHIPPED] }));
  });

  test('a shipped order shows its label as a link to the PDF with what it cost', async ({ page }) => {
    await page.route(`**/api/admin/orders/${SHIPPED.id}/label`, route => route.fulfill({ json: {
      carrier: 'USPS', trackingNumber: SHIPPED.trackingNumber, labelUrl: 'https://deliver.goshippo.com/label.pdf',
      cost: 7.58, provider: 'shippo' } }));
    await page.goto('/#/admin/orders');

    await page.getByTestId('aor-label').click();

    const link = page.getByTestId('aor-label-link');
    await expect(link).toHaveText('Print label ($7.58)');
    await expect(link).toHaveAttribute('href', 'https://deliver.goshippo.com/label.pdf');
    await expect(link).toHaveAttribute('target', '_blank');
    await expect(page.getByTestId('aor-label')).toHaveCount(0);
  });

  test('with simulated shipping the admin is told there is no label', async ({ page }) => {
    await page.route(`**/api/admin/orders/${SHIPPED.id}/label`, route => route.fulfill({ status: 404, json: {
      status: 404, error: 'Not Found', message: 'This order has no shipping label', path: '/api/admin/orders/987654/label' } }));
    await page.goto('/#/admin/orders');

    await page.getByTestId('aor-label').click();

    await expect(page.getByText('This order has no shipping label')).toBeVisible();
    await expect(page.getByTestId('aor-label-link')).toHaveCount(0);
  });
});
