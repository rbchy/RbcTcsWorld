import { useEffect, useState } from 'react';
import axios from 'axios';

/**
 * Whether the shop sells its own products (ADR-0026), read once from GET /api/store/mode. STORE_SALES=off makes the
 * site a partner (affiliate) site: no cart, checkout, orders, wallet, coupon banner or shop policies are shown.
 * Until the answer arrives the full shop is assumed (the backend closes buying either way).
 */
let sales = true;
let loaded = null;
const listeners = new Set();

export function loadStoreMode() {
  if (!loaded) {
    loaded = axios.get('/api/store/mode')
      .then(r => { sales = r.data?.sales !== false; listeners.forEach(f => f(sales)); })
      .catch(() => {});
  }
  return loaded;
}

/** true: the full shop; false: a partner site. */
export function useSales() {
  const [value, setValue] = useState(sales);
  useEffect(() => {
    listeners.add(setValue);
    setValue(sales);
    loadStoreMode();
    return () => listeners.delete(setValue);
  }, []);
  return value;
}

/** Pages of the shop itself; on a partner site they show a short note instead. */
export const SHOP_ROUTES = ['/cart', '/checkout', '/orders', '/addresses', '/payment-methods', '/returns-policy', '/shipping-policy'];
