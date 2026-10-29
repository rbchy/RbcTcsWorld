import { test, expect, signIn } from '../support/fixtures';
import type { Page } from '@playwright/test';

/**
 * Product photo upload in the admin form (REQ-CAT-15, ADR-0017): the photo is prepared in the browser, stored by
 * the backend as a re-encoded JPEG and becomes the product's picture in the storefront.
 */

/** A photo drawn in the browser itself (no image files in the repository): a random colour, so it is new each run. */
async function drawnPhoto(page: Page, type: 'image/png' | 'image/webp', w = 1200, h = 900): Promise<Buffer> {
  const base64 = await page.evaluate(([t, width, height]) => {
    const c = document.createElement('canvas');
    c.width = width as number;
    c.height = height as number;
    const g = c.getContext('2d')!;
    g.fillStyle = `hsl(${Math.floor(Math.random() * 360)} 70% 50%)`;
    g.fillRect(0, 0, c.width, c.height);
    g.fillStyle = '#c00';
    g.fillRect(Math.random() * 900, Math.random() * 600, 200, 200);
    return c.toDataURL(t as string).split(',')[1];
  }, [type, w, h]);
  return Buffer.from(base64, 'base64');
}

async function openNewProductForm(page: Page, name: string) {
  await page.goto('/#/admin/products');
  await page.getByTestId('admin-product-add').click();
  await page.getByTestId('pf-name').fill(name);
  await page.getByTestId('pf-sku').fill(`PH-${Date.now().toString(36).toUpperCase()}${Math.floor(Math.random() * 99)}`);
  await page.getByTestId('pf-price').fill('14.50');
  await page.getByTestId('pf-stock').fill('4');
}

const PICTURE = /^\/api\/products\/pictures\/[0-9a-f]{64}\.jpg$/;

test('the admin uploads a photo and the storefront shows it', async ({ page, api, request }) => {
  await signIn(page, await api.admin());
  const name = `Photo vase ${Date.now().toString(36)}`;
  await openNewProductForm(page, name);
  const photo = await drawnPhoto(page, 'image/png');

  await page.getByTestId('pf-photo').setInputFiles({ name: 'vase.png', mimeType: 'image/png', buffer: photo });
  await expect(page.getByTestId('pf-photo-ok')).toHaveText('Photo uploaded. Save the product to use it.');
  await expect(page.getByTestId('pf-image')).toHaveValue(PICTURE);
  const url = await page.getByTestId('pf-image').inputValue();
  const preview = page.getByTestId('pf-image-preview');
  await expect(preview).toHaveAttribute('src', url);
  await expect(preview).toHaveAttribute('data-fallback', 'false');
  await expect.poll(() => preview.evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth)).toBeGreaterThan(0);

  await page.getByTestId('pf-save').click();
  await expect(page.getByTestId('admin-notice')).toBeVisible();

  const found = await (await request.get('/api/products', { params: { q: name } })).json();
  expect(found).toHaveLength(1);
  expect(found[0].imageUrl).toBe(url);
  await page.goto(`/#/products/${found[0].id}`);
  const image = page.getByTestId('detail-image');
  await expect(image).toHaveAttribute('src', url);
  await expect.poll(() => image.evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth)).toBeGreaterThan(0);
  await expect(image).toHaveAttribute('data-fallback', 'false');
});

test('a WebP photo is turned into a JPEG in the browser before it is sent', async ({ page, api }) => {
  await signIn(page, await api.admin());
  await openNewProductForm(page, `WebP lamp ${Date.now().toString(36)}`);
  const photo = await drawnPhoto(page, 'image/webp');
  const sent = page.waitForRequest(r => r.url().endsWith('/api/products/pictures') && r.method() === 'POST');
  await page.getByTestId('pf-photo').setInputFiles({ name: 'lamp.webp', mimeType: 'image/webp', buffer: photo });
  // The backend refuses WebP, so an accepted upload already proves the conversion. Where the browser reports the
  // multipart body (not every engine does for file parts), it must also say JPEG.
  const body = (await sent).postDataBuffer()?.toString('latin1');
  if (body) {
    expect(body).toContain('Content-Type: image/jpeg');
    expect(body).toContain('filename="lamp.jpg"');
  }
  await expect(page.getByTestId('pf-image')).toHaveValue(PICTURE);
});

test('a file that is not a photo gets a clear message and the picture field stays as it was', async ({ page, api }) => {
  await signIn(page, await api.admin());
  await openNewProductForm(page, `Not a photo ${Date.now().toString(36)}`);
  await page.getByTestId('pf-image').fill('/products/home-mug-002.svg');
  await page.getByTestId('pf-photo').setInputFiles({ name: 'notes.jpg', mimeType: 'image/jpeg', buffer: Buffer.from('just some text') });
  await expect(page.getByTestId('pf-photo-error')).toContainText('JPEG or PNG', { timeout: 20_000 });
  await expect(page.getByTestId('pf-image')).toHaveValue('/products/home-mug-002.svg');
});
