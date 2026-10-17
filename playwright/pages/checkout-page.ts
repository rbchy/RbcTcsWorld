import { expect, Page } from '@playwright/test';

export interface Address {
  fullName: string; line1: string; line2?: string; city: string; state: string; zip: string; phone?: string;
}

/** A valid delivery address (same as the Java suite's TestData.ADDRESS). */
export const ADDRESS: Address = {
  fullName: 'Ada Lovelace', line1: '12 Main St', line2: 'Apt 4', city: 'Pittsburgh', state: 'PA', zip: '15213',
  phone: '412-555-0100',
};

/**
 * Checkout page (frontend/src/pages/Checkout.jsx). The address fields are found by their visible label, the way
 * a screen reader announces them - a field without a proper label fails here before axe even runs.
 */
export class CheckoutPage {
  constructor(private readonly page: Page) {}

  async open() {
    await this.page.goto('/#/checkout');
    await expect(this.page.getByTestId('price-total')).toBeVisible();
    return this;
  }

  async fillAddress(a: Address) {
    await this.page.getByLabel('Full name', { exact: true }).fill(a.fullName);
    await this.page.getByLabel('Street address', { exact: true }).fill(a.line1);
    await this.page.getByLabel('Apartment, suite (optional)', { exact: true }).fill(a.line2 ?? '');
    await this.page.getByLabel('City', { exact: true }).fill(a.city);
    await this.page.getByLabel('State', { exact: true }).selectOption(a.state);
    await this.page.getByLabel('ZIP code', { exact: true }).fill(a.zip);
    await this.page.getByLabel('Phone (optional)', { exact: true }).fill(a.phone ?? '');
  }

  fieldError(field: string) {
    return this.page.getByTestId(`addr-${field}-error`);
  }

  placeOrderButton() {
    return this.page.getByTestId('place-order');
  }
}
