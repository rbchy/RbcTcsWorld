import { test, expect, signIn } from '../support/fixtures';

/** Product variants in the browser: one catalog card per item, colour and size pickers, admin form (REQ-CAT-07..10). */

test('the T-shirt is one card with its options; colour and size open the right variant @mobile', async ({ signedIn: page }) => {
  await page.goto('/#/');
  await page.getByTestId('search-input').fill('Classic Cotton T-Shirt');
  const card = page.getByTestId('product-card');
  await expect(card).toHaveCount(1);
  await expect(card.getByTestId('card-options')).toContainText('8 options: 2 colours, 4 sizes');
  await expect(card.getByTestId('product-price')).toHaveText('From $19.99');
  await expect(card.getByTestId('add-to-cart')).toHaveCount(0);              // the size must be chosen first
  await card.getByTestId('choose-options').click();

  await expect(page.getByTestId('variant-selected')).toContainText('Navy / S');
  await expect(page.getByTestId('pick-color-navy')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByTestId('pick-size-xl')).toHaveClass(/sold-out/);    // Navy XL: none left

  await page.getByTestId('pick-color-heather-grey').click();                 // same size in the other colour
  await expect(page.getByTestId('detail-sku')).toHaveText('TEE-CLS-GRY-S');
  await expect(page.getByTestId('detail-image')).toHaveAttribute('src', '/products/tee-grey.svg');

  await page.getByTestId('pick-size-m').click();                             // Grey M is sold out: can be seen, not bought
  await expect(page.getByTestId('detail-sku')).toHaveText('TEE-CLS-GRY-M');
  await expect(page.getByTestId('detail-stock')).toHaveText('Out of stock');
  await expect(page.getByTestId('detail-add-to-cart')).toBeDisabled();

  await page.getByTestId('pick-size-xl').click();
  await expect(page.getByTestId('detail-price')).toHaveText('$21.99');      // XL costs more
  await page.getByTestId('detail-qty').fill('11');
  await expect(page.getByTestId('detail-qty-error')).toHaveText('Enter a whole number from 1 to 10.');
  await expect(page.getByTestId('detail-add-to-cart')).toBeDisabled();
  await page.getByTestId('detail-qty').fill('2');
  await page.getByTestId('detail-add-to-cart').click();
  await expect(page.getByTestId('detail-cart-notice')).toContainText('2 item(s) in your cart');
  await expect(page.getByTestId('top-cart-count')).toHaveText('2');

  await page.goto('/#/cart');
  await expect(page.getByTestId('cart-line')).toContainText('Classic Cotton T-Shirt (Heather Grey, XL)');
});

test('the store admin builds a variant group in the product form, with the same rules as the API', async ({ page, api }) => {
  await signIn(page, await api.admin());
  const tag = Date.now().toString(36).toUpperCase();
  await page.goto('/#/admin/products');

  const add = async (sku: string, size: string, color: string) => {
    await page.getByTestId('admin-product-add').click();
    await page.getByTestId('pf-name').fill(`Polo ${tag}`);
    await page.getByTestId('pf-sku').fill(sku);
    await page.getByTestId('pf-category').fill('clothing');
    await page.getByTestId('pf-price').fill('25.00');
    await page.getByTestId('pf-stock').fill('4');
    await page.getByTestId('pf-group').fill(`polo-${tag}`);
    await page.getByTestId('pf-size').fill(size);
    await page.getByTestId('pf-color').fill(color);
    await page.getByTestId('pf-save').click();
  };

  // the browser checks first: a size without a group, a colour with digits
  await page.getByTestId('admin-product-add').click();
  await page.getByTestId('pf-size').fill('M');
  await page.getByTestId('pf-color').fill('Red5');
  await page.getByTestId('pf-save').click();
  await expect(page.getByTestId('pf-group-error')).toHaveText('A size or colour needs a variant group.');
  await expect(page.getByTestId('pf-color-error')).toHaveText('Letters, spaces and - only (up to 30).');
  await page.getByTestId('pf-cancel').click();

  await add(`POLO-${tag}-W-M`, 'M', 'White');
  await expect(page.getByTestId('admin-notice')).toBeVisible();
  await add(`POLO-${tag}-W-M2`, 'm', 'white');                               // the server refuses a twin
  await expect(page.getByTestId('admin-error')).toContainText('already has');
  await page.getByTestId('pf-sku').fill(`POLO-${tag}-B-L`);
  await page.getByTestId('pf-size').fill('L');
  await page.getByTestId('pf-color').fill('Black');
  await page.getByTestId('pf-save').click();
  await expect(page.getByTestId('admin-notice')).toBeVisible();

  await page.getByTestId('admin-product-search').fill(`Polo ${tag}`);
  await expect(page.getByTestId('admin-product-row')).toHaveCount(2);        // the admin sees every variant
  await expect(page.getByTestId('apr-variant').first()).toContainText(`POLO-${tag}`);

  await page.goto('/#/');
  await page.getByTestId('search-input').fill(`Polo ${tag}`);
  await expect(page.getByTestId('product-card')).toHaveCount(1);             // the shopper sees one card
  await expect(page.getByTestId('card-options')).toContainText('2 options: 2 colours, 2 sizes');
});
