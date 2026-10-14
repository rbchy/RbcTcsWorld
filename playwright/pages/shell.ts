import { expect, Locator, Page } from '@playwright/test';

/** Top bar and menu: the parts on every page (frontend/src/Layout.jsx). */
export class Shell {
  readonly logout: Locator;
  readonly exit: Locator;
  readonly menuButton: Locator;
  readonly user: Locator;

  constructor(private readonly page: Page) {
    // by role and accessible name, as a screen reader finds them; on phones the text is visually hidden
    // but still the button's name, so the same locator works on every screen size
    this.logout = page.getByRole('banner').getByRole('button', { name: 'Log out' });
    this.exit = page.getByRole('banner').getByRole('button', { name: 'Exit' });
    this.menuButton = page.getByTestId('menu-toggle');
    this.user = page.getByTestId('nav-user');
  }

  async expectSignedInAs(email: string) {
    await expect(this.user).toHaveText(email);
  }

  async expectSignedOut() {
    await expect(this.page.getByTestId('nav-login')).toBeVisible();
    await expect(this.user).toHaveCount(0);
  }

  stored(key: string) {
    return this.page.evaluate(k => localStorage.getItem(k), key);
  }
}
