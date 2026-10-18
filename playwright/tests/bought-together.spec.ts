import { test, expect } from '../support/fixtures';
import { ADDRESS } from '../pages/checkout-page';

/** "Customers who bought this also bought" on the product page (REQ-CAT-13). */

test('the product page offers what others bought in the same order, and the link opens it @mobile', async ({ page, api }) => {
  const main = await api.createProduct({ price: 14, stock: 30 });
  const partner = await api.createProduct({ price: 9, stock: 30 });
  const lonely = await api.createProduct({ price: 7, stock: 30 });
  for (let i = 0; i < 2; i++) {                                                 // two customers bought both in one order
    const buyer = await api.register();
    await api.addToCart(buyer.token, main.id, 1);
    await api.addToCart(buyer.token, partner.id, 1);
    const r = await page.request.post('/api/orders', { headers: { Authorization: `Bearer ${buyer.token}` }, data: { shippingAddress: ADDRESS } });
    expect(r.status()).toBe(201);
  }

  await page.goto(`/#/products/${main.id}`);
  const row = page.getByTestId('bought-together');
  await expect(row).toBeVisible();
  await expect(row).toContainText('Customers who bought this also bought');
  await expect(row.getByTestId('bought-together-product')).toHaveCount(1);
  await expect(row.getByTestId('bought-together-product')).toContainText(partner.name);
  await expect(row.getByTestId('bought-together-product')).toContainText('$9.00');
  await row.getByTestId('bought-together-product').getByRole('link').click();
  await expect(page).toHaveURL(new RegExp(`#/products/${partner.id}$`));

  await page.goto(`/#/products/${lonely.id}`);                                   // nobody bought it: no row at all
  await expect(page.getByTestId('detail-name')).toBeVisible();
  await expect(page.getByTestId('bought-together')).toHaveCount(0);
});
