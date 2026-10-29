import { test, expect } from '../support/fixtures';

/**
 * What search engines see (REQ-SEO-01..03, ADR-0019): real product addresses, titles, descriptions, canonical links,
 * schema.org Product data, noindex on private pages, robots.txt and sitemap.xml at the site root.
 */

const slugOf = (name: string) => name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

test('a product opens at its real address with title, description, canonical link and Product data', async ({ page, api }) => {
  const p = await api.createProduct({ price: 18.25, stock: 4, imageUrl: '/products/home-mug-002.svg' });
  const path = `/products/${p.id}/${slugOf(p.name)}`;
  await page.goto(path);

  await expect(page.getByTestId('detail-name')).toHaveText(p.name);
  await expect(page).toHaveTitle(`${p.name} | RbcTcsWorld Shop`);
  await expect(page.locator('meta[name="description"]')).toHaveAttribute('content', new RegExp(`${p.name} for \\$18\\.25`));
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', 'index, follow');
  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', new URL(path, page.url()).href);

  const data = JSON.parse(await page.locator('script#ld-json').textContent() ?? '{}');
  expect(data['@type']).toBe('Product');
  expect(data.name).toBe(p.name);
  expect(data.sku).toBe(p.sku);
  expect(data.image).toBe(new URL('/products/home-mug-002.svg', page.url()).href);
  expect(data.offers).toMatchObject({ price: '18.25', priceCurrency: 'USD', availability: 'https://schema.org/InStock' });

  // the same product through the app's own #/ link has the same canonical address
  await page.goto(`/#/products/${p.id}`);
  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', new URL(path, page.url()).href);
});

test('public pages are indexable, personal pages are not @mobile', async ({ page }) => {
  await page.goto('/contact');
  await expect(page.getByTestId('contact-page')).toBeVisible();
  await expect(page).toHaveTitle('Contact us | RbcTcsWorld Shop');
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', 'index, follow');

  await page.goto('/#/cart');
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', 'noindex, follow');
  await expect(page.locator('link[rel="canonical"]')).toHaveCount(0);

  await page.goto('/#/privacy');
  await expect(page).toHaveTitle('Privacy policy | RbcTcsWorld Shop');
  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', /\/privacy$/);
});

test('robots.txt and sitemap.xml are served at the site root', async ({ request, api }) => {
  const p = await api.createProduct();
  const sitemap = await request.get('/sitemap.xml');
  expect(sitemap.status()).toBe(200);
  expect(sitemap.headers()['content-type']).toContain('xml');
  const xml = await sitemap.text();
  expect(xml).toContain('<urlset');
  expect(xml).toContain(`/products/${p.id}/${slugOf(p.name)}</loc>`);
  expect(xml).toContain('/contact</loc>');

  const robots = await request.get('/robots.txt');
  expect(robots.status()).toBe(200);
  // CI runs on http://localhost: not a public store, so search engines are told to stay away
  expect(await robots.text()).toContain('Disallow: /');
});
