import { useEffect, useState } from 'react';

/**
 * Tiny global session store in localStorage, change events for the header:
 * token (access, 15 min) + refreshToken (14 days, rotated) + email + role.
 */
const listeners = new Set();

export function getSession() {
  const token = localStorage.getItem('token');
  return token ? { token, email: localStorage.getItem('email'), role: localStorage.getItem('role') } : null;
}

export function saveSession({ token, email, role, refreshToken }) {
  localStorage.setItem('token', token);
  if (refreshToken) localStorage.setItem('refreshToken', refreshToken);
  localStorage.setItem('email', email);
  localStorage.setItem('role', role);
  listeners.forEach(l => l());
}

export function clearSession() {
  ['token', 'refreshToken', 'email', 'role'].forEach(k => localStorage.removeItem(k));
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

/** Simple hash router: #/cart, #/orders/12 ... (works with any static server, easy to deep-link in tests). */
export function useRoute() {
  const read = () => window.location.hash.replace(/^#/, '') || '/';
  const [route, setRoute] = useState(read());
  useEffect(() => {
    const onChange = () => setRoute(read());
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  return route;
}

export const go = path => { window.location.hash = path; };
