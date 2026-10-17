import { test, expect } from '../support/fixtures';
import { CatalogPage } from '../pages/catalog-page';

const SEEDED = ['ELEC-MOUSE-001', 'ELEC-KEYB-002', 'ELEC-MON-003', 'ELEC-HUB-004',
  'HOME-BOTL-001', 'HOME-MUG-002', 'BOOK-QA-001', 'ELEC-HEAD-005'];

test.describe('Catalog and product pictures', () => {
  test('every seeded product shows its own picture, and the browser really decodes it @mobile', async ({ page }) => {
    const catalog = await new CatalogPage(page).open();
    for (const sku of SEEDED) {
      await test.step(sku, async () => {
        const pic = await catalog.picture(sku);
        expect(pic.src).toBe(`/products/${sku.toLowerCase()}.svg`);
        expect(pic.fallback).toBe(false);
        expect(pic.alt, 'decorative on the card: the name is right below it').toBe('');
      });
    }
  });

  test('a product without a picture shows its category picture', async ({ page, api }) => {
    const product = await api.createProduct({ category: 'books' });
    const catalog = await new CatalogPage(page).open();
    await catalog.searchFor(product.name);
    const pic = await catalog.picture(product.sku);
    expect(pic).toMatchObject({ src: '/products/books.svg', fallback: true });
  });

  test('a picture link that points to a missing file falls back instead of a broken image', async ({ page, api }) => {
    const product = await api.createProduct({ imageUrl: '/products/this-file-does-not-exist.png' });
    const catalog = await new CatalogPage(page).open();
    await catalog.searchFor(product.name);
    expect(await catalog.picture(product.sku)).toMatchObject({ src: '/products/product.svg', fallback: true });
  });

  test('search filters the grid by product name @mobile', async ({ page }) => {
    const catalog = await new CatalogPage(page).open();
    await catalog.searchFor('mouse');
    await expect(catalog.card('ELEC-MOUSE-001')).toBeVisible();
    await expect(catalog.card('ELEC-KEYB-002')).toHaveCount(0);
  });

  test('product page shows the large picture with the product name as its description', async ({ page }) => {
    const catalog = await new CatalogPage(page).open();
    await catalog.card('HOME-MUG-002').getByTestId('product-name').click();
    await expect(page.getByTestId('detail-name')).toHaveText('Ceramic Coffee Mug Set');
    const img = page.getByTestId('detail-image');
    await expect(img).toHaveAttribute('alt', 'Ceramic Coffee Mug Set');
    await expect(img).toHaveAttribute('src', '/products/home-mug-002.svg');
    await expect.poll(() => img.evaluate((i: HTMLImageElement) => i.naturalWidth)).toBeGreaterThan(0);
  });

  test('the product page suggests similar products from the same category', async ({ page, api }) => {
    const category = `pw-sim-${Date.now()}`;
    const a = await api.createProduct({ price: 10, category });
    const b = await api.createProduct({ price: 11, category });
    await page.goto(`/#/products/${a.id}`);
    const similar = page.getByTestId('similar-products');
    await expect(similar).toContainText('Similar products');
    await expect(similar.getByTestId('similar-product')).toHaveCount(1);
    await similar.getByText(b.name).click();
    await expect(page.getByTestId('detail-name')).toHaveText(b.name);
  });
});
