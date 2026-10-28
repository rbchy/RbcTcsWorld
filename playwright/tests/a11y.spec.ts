import AxeBuilder from '@axe-core/playwright';
import { test, expect } from '../support/fixtures';

const PAGES = [
  { name: 'catalog', path: '/#/', ready: 'product-card' },
  { name: 'product', path: '/#/products/1', ready: 'detail-image' },
  { name: 'goodbye', path: '/#/goodbye', ready: 'goodbye' },
  { name: 'help', path: '/#/help', ready: 'help-topic' },
  { name: 'forgot password', path: '/#/forgot-password', ready: 'forgot-email' },
  { name: 'privacy policy', path: '/#/privacy', ready: 'legal-effective' },
  { name: 'return policy', path: '/#/returns-policy', ready: 'legal-return-days' },
];

test.describe('Accessibility (WCAG 2.1 AA, axe-core)', () => {
  for (const p of PAGES) {
    test(`${p.name} has no serious or critical violation @mobile`, async ({ page }) => {
      await page.goto(p.path);
      await expect(page.getByTestId(p.ready).first()).toBeVisible();
      const result = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
      const blocking = result.violations.filter(v => v.impact === 'serious' || v.impact === 'critical');
      expect(blocking.map(v => `${v.id}: ${v.nodes.length} element(s) - ${v.help}`)).toEqual([]);
    });
  }
});
