import { APIRequestContext, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';

/**
 * Test data through the real API (same origin as the storefront: /api is proxied).
 * UI tests only drive the part they are about; everything else is set up here, fast and independent.
 */
export const PASSWORD = 'Password1!';
export const ADMIN_EMAIL = process.env.ADMIN_EMAIL ?? 'admin@rbctcsworld.com';
export const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD ?? 'Admin@12345';

export interface Session { email: string; token: string; refreshToken: string; role: string }
export interface Product { id: number; sku: string; name: string; imageUrl: string | null; category: string | null }

export const unique = (prefix: string) => `${prefix}-${randomUUID().slice(0, 8)}`.toUpperCase();

export class ShopApi {
  constructor(private readonly request: APIRequestContext) {}

  async register(email = `pw-${randomUUID()}@test.com`): Promise<Session> {
    const r = await this.request.post('/api/auth/register', { data: { email, password: PASSWORD } });
    expect(r.status(), await r.text()).toBe(201);
    return { email, ...(await r.json()) };
  }

  async login(email: string, password: string): Promise<Session> {
    const r = await this.request.post('/api/auth/login', { data: { email, password } });
    expect(r.status(), await r.text()).toBe(200);
    return { email, ...(await r.json()) };
  }

  admin(): Promise<Session> {
    return this.login(ADMIN_EMAIL, ADMIN_PASSWORD);
  }

  /** A product only this test uses: its name contains the SKU, because the search matches names. */
  async createProduct(opts: { price?: number; stock?: number; category?: string; imageUrl?: string } = {}): Promise<Product> {
    const admin = await this.admin();
    const sku = unique('PW');
    const r = await this.request.post('/api/products', {
      headers: { Authorization: `Bearer ${admin.token}` },
      data: { name: `PW lamp ${sku}`, sku, category: opts.category ?? 'qa', price: opts.price ?? 12.5,
              stock: opts.stock ?? 20, imageUrl: opts.imageUrl ?? null },
    });
    expect(r.status(), await r.text()).toBe(201);
    return r.json();
  }

  async product(id: number): Promise<Product> {
    const r = await this.request.get(`/api/products/${id}`);
    expect(r.ok()).toBeTruthy();
    return r.json();
  }

  async addToCart(token: string, productId: number, quantity = 1) {
    const r = await this.request.post('/api/cart/items', {
      headers: { Authorization: `Bearer ${token}` }, data: { productId, quantity },
    });
    expect(r.status(), await r.text()).toBe(201);
  }

  async order(token: string, id: number): Promise<{ id: number; status: string; total: number }> {
    const r = await this.request.get(`/api/orders/${id}`, { headers: { Authorization: `Bearer ${token}` } });
    expect(r.ok()).toBeTruthy();
    return r.json();
  }
}
