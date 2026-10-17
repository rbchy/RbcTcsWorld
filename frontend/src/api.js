import axios from 'axios';
import { clearSession, saveSession } from './session';

/** All calls go to /api (proxied to the backend). The JWT from login is sent automatically. */
const api = axios.create({ baseURL: '/api' });

api.interceptors.request.use(config => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

/*
 * Silent refresh. The access token lives 15 minutes; when a call answers 401 the refresh token buys a new
 * pair and the call is repeated once, so the customer never notices. Rules:
 *  - one refresh at a time: parallel 401s wait for the same request (a second refresh with the same token
 *    would look like token theft and end the session - see RefreshTokenService);
 *  - if another tab already refreshed, its new token is simply used;
 *  - when the refresh fails too, the session is over: it is cleared and protected pages show the login.
 */
let refreshing = null;

function refreshSession() {
  if (!refreshing) {
    const refreshToken = localStorage.getItem('refreshToken');
    refreshing = (refreshToken
      ? axios.post('/api/auth/refresh', { refreshToken }).then(r => { saveSession(r.data); return r.data.token; })
      : Promise.reject(new Error('No refresh token')))
      .finally(() => { refreshing = null; });
  }
  return refreshing;
}

/** Every cart answer carries totalQuantity: tell the top bar, so the badge on the cart icon is always right. */
function announceCart(response) {
  const n = response?.data?.totalQuantity;
  const url = response?.config?.url || '';
  if (url.startsWith('/cart') && Number.isFinite(n)) {
    window.dispatchEvent(new CustomEvent('cart-count', { detail: n }));
  } else if (response?.config?.method === 'post' && (url === '/orders' || url.endsWith('/move-to-cart'))) {
    window.dispatchEvent(new CustomEvent('cart-refresh'));          // the cart changed, but this answer has no count
  }
  return response;
}

api.interceptors.response.use(announceCart, async error => {
  const original = error.config;
  if (error.response?.status !== 401 || !original || original._retried || original.url?.startsWith('/auth/')) {
    throw error;
  }
  original._retried = true;
  const sent = original.headers?.Authorization;
  const current = localStorage.getItem('token');
  let token;
  try {
    token = current && sent !== `Bearer ${current}` ? current : await refreshSession();
  } catch (refreshError) {
    // refused (expired, logged out, reused) or nothing to refresh with: the session is over.
    // A network error keeps the session - the next call may work again.
    if (refreshError.response || !localStorage.getItem('refreshToken')) clearSession();
    throw error;
  }
  original.headers.Authorization = `Bearer ${token}`;
  return api(original);
});

/** Ends the session here AND on the server (the refresh token stops working). Never fails for the user. */
export async function logout() {
  const refreshToken = localStorage.getItem('refreshToken');
  clearSession();
  if (refreshToken) await axios.post('/api/auth/logout', { refreshToken }).catch(() => {});
}

/** Turns the backend's JSON error ({message, fieldErrors}) into one readable sentence. */
export function errorText(err) {
  const data = err?.response?.data;
  if (!data) return 'Cannot reach the server. Is the backend running on port 8081?';
  const fields = data.fieldErrors && Object.entries(data.fieldErrors).map(([f, m]) => `${f}: ${m}`).join(', ');
  return fields ? `${data.message} (${fields})` : data.message || `Error ${err.response.status}`;
}

export const money = v => `$${Number(v ?? 0).toFixed(2)}`;

export default api;
