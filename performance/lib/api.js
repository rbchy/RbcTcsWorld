import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, ADMIN_EMAIL, ADMIN_PASSWORD, PASSWORD, CARD_OK } from './config.js';

const JSON_HEADERS = { 'Content-Type': 'application/json', Accept: 'application/json' };

function params(token, name, extra = {}) {
  const headers = token ? { ...JSON_HEADERS, Authorization: `Bearer ${token}` } : JSON_HEADERS;
  // "name" groups URLs with ids (/api/orders/123/pay) into ONE metric series: /api/orders/{id}/pay
  // "kind" splits reads from writes so each gets its own latency target (see SLO in config.js)
  const kind = name.startsWith('GET') ? 'read' : 'write';
  const p = { headers, tags: { name, kind, ...(extra.tags || {}) } };
  if (extra.responseCallback) p.responseCallback = extra.responseCallback;
  return p;
}

/** Every request name used above - the report shows a latency row for each one. */
export const ENDPOINTS = [
  'GET /api/products', 'GET /api/products?q=', 'GET /api/products/{id}', 'GET /api/products/{id}/reviews',
  'POST /api/auth/register', 'POST /api/auth/login', 'POST /api/cart/items', 'POST /api/checkout/quote',
  'POST /api/orders', 'POST /api/orders/{id}/pay',
];

const post = (path, body, token, name, extra) => http.post(`${BASE_URL}${path}`, body === null ? null : JSON.stringify(body), params(token, name, extra));
const get = (path, token, name, extra) => http.get(`${BASE_URL}${path}`, params(token, name, extra));

// ---------- auth ----------

export function login(email, password) {
  const r = post('/api/auth/login', { email, password }, null, 'POST /api/auth/login');
  check(r, { 'login 200': (x) => x.status === 200 });
  return r.status === 200 ? r.json('token') : null;
}

export function adminToken() {
  const t = login(ADMIN_EMAIL, ADMIN_PASSWORD);
  if (!t) throw new Error(`Admin login failed - is the backend running on ${BASE_URL}?`);
  return t;
}

/** Registers a brand-new customer and returns { email, token }. */
export function newCustomer(prefix = 'perf') {
  const email = `${prefix}-${__VU}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@load.test`;
  const r = post('/api/auth/register', { email, password: PASSWORD }, null, 'POST /api/auth/register');
  check(r, { 'register 201': (x) => x.status === 201 });
  return r.status === 201 ? { email, token: r.json('token') } : null;
}

// ---------- catalog (public) ----------

export const listProducts = () => get('/api/products', null, 'GET /api/products');
export const search = (q) => get(`/api/products?q=${encodeURIComponent(q)}`, null, 'GET /api/products?q=');
export const product = (id) => get(`/api/products/${id}`, null, 'GET /api/products/{id}');
export const reviews = (id, sort = 'newest') => get(`/api/products/${id}/reviews?sort=${sort}`, null, 'GET /api/products/{id}/reviews');

export function createProduct(token, name, price, stock) {
  const sku = `PERF-${Date.now()}-${Math.floor(Math.random() * 1e6)}`;
  const r = post('/api/products', { name, sku, category: 'perf', price, stock }, token, 'POST /api/products');
  if (r.status !== 201) throw new Error(`Could not create product: ${r.status} ${r.body}`);
  return r.json('id');
}

// ---------- shopping ----------

/** 409 (stock) is an expected business answer in some tests, so it can be excluded from http_req_failed. */
export const expect = (...codes) => http.expectedStatuses(...codes);

export const addToCart = (token, productId, quantity, extra) =>
  post('/api/cart/items', { productId, quantity }, token, 'POST /api/cart/items', extra);
export const clearCart = (token) => http.del(`${BASE_URL}/api/cart`, null, params(token, 'DELETE /api/cart'));
export const quote = (token, couponCode) => post('/api/checkout/quote', couponCode ? { couponCode } : {}, token, 'POST /api/checkout/quote');
// every order needs a US delivery address (V12)
export const SHIPPING_ADDRESS = {
  fullName: 'K6 Buyer', line1: '1 Load Test Way', city: 'Pittsburgh', state: 'PA', zip: '15213',
};
export const placeOrder = (token, extra) =>
  post('/api/orders', { shippingAddress: SHIPPING_ADDRESS }, token, 'POST /api/orders', extra);
export const pay = (token, orderId) =>
  post(`/api/orders/${orderId}/pay`, { cardNumber: CARD_OK, expiryMonth: 12, expiryYear: 2035, cvv: '123' }, token, 'POST /api/orders/{id}/pay');
export const myOrders = (token) => get('/api/orders', token, 'GET /api/orders');
export const addToWishlist = (token, productId) =>
  post('/api/wishlist', { productId }, token, 'POST /api/wishlist', { responseCallback: expect(200, 201) });
