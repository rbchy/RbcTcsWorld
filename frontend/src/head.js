import { useEffect } from 'react';

/**
 * Page title, description, canonical address and structured data for search engines (ADR-0019).
 * Search engines run the page's JavaScript, read these tags and ignore everything after "#": so a page that should be
 * found names its real path (/products/12/travel-mug) as canonical; private pages (cart, orders, admin) say noindex.
 * The public demo is never indexed (VITE_DEMO build).
 */
export const SHOP = 'RbcTcsWorld Shop';
const DEMO = import.meta.env.VITE_DEMO === 'true';

/** Same slug as the backend's sitemap (SeoService.productPath). */
export function productPath(id, name) {
  const s = (name || '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
  const cut = s.length > 80 ? s.slice(0, 80).replace(/-$/, '') : s;
  return `/products/${id}${cut ? `/${cut}` : ''}`;
}

function tag(selector, create) {
  let el = document.head.querySelector(selector);
  if (!el) { el = create(); document.head.appendChild(el); }
  return el;
}

function meta(name, content) {
  const el = tag(`meta[name="${name}"]`, () => Object.assign(document.createElement('meta'), { name }));
  el.setAttribute('content', content);
}

/** Applies a head description: {title, description, path (canonical, indexable) | index: false, jsonLd}. */
export function applyHead({ title, description, path, index = !!path, jsonLd } = {}) {
  document.title = title ? `${title} | ${SHOP}` : SHOP;
  if (description) meta('description', description);
  meta('robots', index && !DEMO ? 'index, follow' : 'noindex, follow');
  const canonical = document.head.querySelector('link[rel="canonical"]');
  if (path && index) {
    tag('link[rel="canonical"]', () => Object.assign(document.createElement('link'), { rel: 'canonical' }))
      .setAttribute('href', `${window.location.origin}${path}`);
  } else if (canonical) {
    canonical.remove();
  }
  const old = document.getElementById('ld-json');
  if (old) old.remove();
  if (jsonLd) {
    const s = document.createElement('script');
    s.type = 'application/ld+json';
    s.id = 'ld-json';
    s.textContent = JSON.stringify(jsonLd).replace(/</g, '\\u003c');   // a product name can never close the script
    document.head.appendChild(s);
  }
}

/** React hook form; pass null to leave the head alone (a child page sets it). */
export function useHead(head) {
  const key = head ? JSON.stringify(head) : null;
  useEffect(() => { if (head) applyHead(head); }, [key]);
}
