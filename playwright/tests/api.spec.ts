import { test, expect } from '../support/fixtures';

/** Playwright as an API client: the same contract the Java/REST Assured suite checks, from another tool. */
test.describe('API through the storefront origin', () => {
  test('catalog is public, paged, and every product has the agreed fields', async ({ request }) => {
    const r = await request.get('/api/products?size=5');
    expect(r.status()).toBe(200);
    expect(Number(r.headers()['x-total-count'])).toBeGreaterThanOrEqual(8);
    const products = await r.json();
    expect(products).toHaveLength(5);
    for (const p of products) {
      expect(Object.keys(p).sort()).toEqual(
        ['active', 'category', 'color', 'id', 'imageUrl', 'name', 'price', 'ratingAverage', 'ratingCount', 'size', 'sku', 'stock', 'variantGroup']);
    }
  });

  for (const bad of ['javascript:alert(1)', 'http://plain.example.com/a.png', 'data:image/png;base64,AAAA', '/products/../x.svg']) {
    test(`unsafe picture link "${bad}" is refused with 400`, async ({ request, api }) => {
      const admin = await api.admin();
      const r = await request.post('/api/products', {
        headers: { Authorization: `Bearer ${admin.token}` },
        data: { name: 'Bad pic', sku: `PW-BAD-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
                price: 1, stock: 1, imageUrl: bad },
      });
      expect(r.status()).toBe(400);
      expect((await r.json()).fieldErrors.imageUrl).toBeTruthy();
    });
  }

  test('creating a product needs a store admin', async ({ request, customer }) => {
    const data = { name: 'x', sku: 'PW-X', price: 1, stock: 1 };
    expect((await request.post('/api/products', { data })).status()).toBe(401);
    expect((await request.post('/api/products', { data, headers: { Authorization: `Bearer ${customer.token}` } })).status()).toBe(403);
  });
});
