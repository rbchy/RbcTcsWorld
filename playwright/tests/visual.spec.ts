import { test, expect } from '../support/fixtures';
import fs from 'node:fs';
import path from 'node:path';

/**
 * Visual regression of the product pictures (frontend/public/products/*.svg): an accidental change to a
 * drawing fails here with a diff image. Chromium on Linux only (one baseline per picture).
 * After an intended change: npm run update-snapshots, check the new PNGs, commit them.
 */
const DIR = path.join(__dirname, '..', '..', 'frontend', 'public', 'products');
const PICTURES = fs.readdirSync(DIR).filter(f => f.endsWith('.svg')).sort();

test.describe('Product pictures look the same', () => {
  test.use({ viewport: { width: 400, height: 300 } });

  test('there is a picture for every seeded product and every category', () => {
    expect(PICTURES).toEqual(expect.arrayContaining([
      'book-qa-001.svg', 'books.svg', 'elec-head-005.svg', 'elec-hub-004.svg', 'elec-keyb-002.svg', 'elec-mon-003.svg',
      'elec-mouse-001.svg', 'electronics.svg', 'home-botl-001.svg', 'home-mug-002.svg', 'home.svg', 'product.svg']));
  });

  for (const file of PICTURES) {
    test(file, async ({ page }) => {
      const r = await page.goto(`/products/${file}`);
      expect(r?.headers()['content-type']).toContain('image/svg+xml');
      await expect(page).toHaveScreenshot(file.replace('.svg', '.png'));
    });
  }
});
