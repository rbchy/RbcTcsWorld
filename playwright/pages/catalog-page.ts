import { expect, Locator, Page } from '@playwright/test';

/** Catalog (frontend/src/pages/Home.jsx). */
export class CatalogPage {
  readonly search: Locator;
  readonly cards: Locator;
  readonly flash: Locator;

  constructor(private readonly page: Page) {
    this.search = page.getByTestId('search-input');
    this.cards = page.getByTestId('product-card');
    this.flash = page.getByTestId('flash');
  }

  async open() {
    await this.page.goto('/#/');
    await expect(this.cards.first()).toBeVisible();
    return this;
  }

  card(sku: string): Locator {
    return this.page.locator(`[data-testid="product-card"][data-sku="${sku}"]`);
  }

  async searchFor(text: string) {
    await this.search.fill(text);
    // the grid is filtered by the API; wait until every card matches
    await expect(async () => {
      const names = await this.page.getByTestId('product-name').allTextContents();
      expect(names.length).toBeGreaterThan(0);
      for (const n of names) expect(n.toLowerCase()).toContain(text.toLowerCase());
    }).toPass();
  }

  async addToCart(sku: string, quantity = 1) {
    const card = this.card(sku);
    await card.getByTestId('qty-input').fill(String(quantity));
    await card.getByTestId('add-to-cart').click();
    await expect(this.flash).toContainText('Added to cart');
  }

  /** The picture of a card: address, fallback flag, and whether the browser really decoded it. */
  async picture(sku: string) {
    const img = this.card(sku).getByTestId('product-image');
    await img.scrollIntoViewIfNeeded();                 // the catalog loads pictures lazily
    await expect.poll(() => img.evaluate((i: HTMLImageElement) => i.complete && i.naturalWidth > 0)).toBe(true);
    return {
      src: await img.getAttribute('src'),
      fallback: (await img.getAttribute('data-fallback')) === 'true',
      alt: await img.getAttribute('alt'),
    };
  }
}
