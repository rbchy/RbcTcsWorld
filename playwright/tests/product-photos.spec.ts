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

/*
 * DEF-031: the backend decodes at most two photos at a time and answers a third upload with 429 + Retry-After
 * (ADR-0017). The form used to show that "busy" answer as the result; with parallel test workers it replaced the real
 * answer of "a file that is not a photo..." in 10 of 11 CI runs. The form now waits as long as the server asks.
 */
// WebKit does not hand multipart file uploads to page.route (the request goes straight to the server), so the busy
// answer cannot be staged there; the retry logic is plain JavaScript and the same in every engine.
const NO_UPLOAD_ROUTING = 'WebKit sends file uploads past page.route, so a 429 cannot be staged';
const BUSY = { status: 429, headers: { 'Retry-After': '1', 'Content-Type': 'application/json' },
  body: JSON.stringify({ status: 429, message: 'Another photo is being processed; please try again in a moment' }) };

test('a busy server (429 + Retry-After) is waited for, and the photo is uploaded (DEF-031)', async ({ page, api, browserName }) => {
  test.skip(browserName === 'webkit', NO_UPLOAD_ROUTING);
  await signIn(page, await api.admin());
  await openNewProductForm(page, `Busy then ok ${Date.now().toString(36)}`);
  let posts = 0;
  await page.route('**/api/products/pictures', async route => {
    if (route.request().method() !== 'POST') return route.continue();
    posts += 1;
    return posts === 1 ? route.fulfill(BUSY) : route.continue();   // busy once, then the real server answers
  });
  const photo = await drawnPhoto(page, 'image/png', 400, 300);
  await page.getByTestId('pf-photo').setInputFiles({ name: 'cup.png', mimeType: 'image/png', buffer: photo });
  await expect(page.getByTestId('pf-photo-ok')).toHaveText('Photo uploaded. Save the product to use it.');
  await expect(page.getByTestId('pf-image')).toHaveValue(PICTURE);
  await expect(page.getByTestId('pf-photo-error')).toHaveCount(0);
  expect(posts).toBe(2);
});

test('a server that stays busy gets a clear message after a few tries, and the picture field is kept (DEF-031)', async ({ page, api, browserName }) => {
  test.skip(browserName === 'webkit', NO_UPLOAD_ROUTING);
  await signIn(page, await api.admin());
  await openNewProductForm(page, `Always busy ${Date.now().toString(36)}`);
  await page.getByTestId('pf-image').fill('/products/home-mug-002.svg');
  let posts = 0;
  await page.route('**/api/products/pictures', route => {
    if (route.request().method() !== 'POST') return route.continue();
    posts += 1;
    return route.fulfill(BUSY);
  });
  const photo = await drawnPhoto(page, 'image/png', 400, 300);
  await page.getByTestId('pf-photo').setInputFiles({ name: 'cup.png', mimeType: 'image/png', buffer: photo });
  await expect(page.getByTestId('pf-photo-error')).toHaveText('Another photo is being processed; please try again in a moment',
    { timeout: 20_000 });
  expect(posts).toBe(4);                                             // BUSY_TRIES in frontend/src/photo.js
  await expect(page.getByTestId('pf-image')).toHaveValue('/products/home-mug-002.svg');
});
