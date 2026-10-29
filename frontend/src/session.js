import { useEffect, useState } from 'react';

/**
 * Tiny global session store in localStorage, change events for the header:
 * token (access, 15 min) + refreshToken (14 days, rotated) + email + role.
 */
const listeners = new Set();

export function getSession() {
  const token = localStorage.getItem('token');
  return token ? { token, email: localStorage.getItem('email'), role: localStorage.getItem('role'),
    guest: localStorage.getItem('guest') === 'true' } : null;
}

export function saveSession({ token, email, role, refreshToken, guest }) {
  localStorage.setItem('token', token);
  if (guest) localStorage.setItem('guest', 'true'); else localStorage.removeItem('guest');
  if (refreshToken) localStorage.setItem('refreshToken', refreshToken);
  localStorage.setItem('email', email);
  localStorage.setItem('role', role);
  listeners.forEach(l => l());
}

export function clearSession() {
  ['token', 'refreshToken', 'email', 'role', 'guest'].forEach(k => localStorage.removeItem(k));
  listeners.forEach(l => l());
}

export function useSession() {
  const [session, setSession] = useState(getSession());
  useEffect(() => {
    const l = () => setSession(getSession());
    listeners.add(l);
    return () => listeners.delete(l);
  }, []);
  return session;
}

/**
 * Simple hash router: #/cart, #/orders/12 ... (works with any static server, easy to deep-link in tests).
 * A real path without a hash opens the same page (ADR-0019): /products/12/travel-mug is what search engines and
 * shared links use, because they ignore everything after "#". nginx and Vite answer every path with the app.
 */
export function readRoute(hash = window.location.hash, path = window.location.pathname) {
  const fromHash = hash.replace(/^#/, '');
  if (fromHash) return fromHash;
  const p = path.replace(/\/+$/, '');
  return p && p !== '/index.html' ? p : '/';
}

export function useRoute() {
  const [route, setRoute] = useState(readRoute());
  useEffect(() => {
    const onChange = () => setRoute(readRoute());
    window.addEventListener('hashchange', onChange);
    window.addEventListener('popstate', onChange);
    return () => { window.removeEventListener('hashchange', onChange); window.removeEventListener('popstate', onChange); };
  }, []);
  return route;
}

export const go = path => { window.location.hash = path; };
