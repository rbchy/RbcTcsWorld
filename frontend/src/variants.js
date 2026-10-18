/** Product variants (size / colour): helpers shared by the catalog card, the product page and the admin. */

const SIZE_ORDER = ['XXS', 'XS', 'S', 'M', 'L', 'XL', 'XXL', 'XXXL'];

/** Same order as the backend (VariantSummary.SIZES): clothing sizes, then numbers, then the rest A-Z. */
export function compareSizes(a, b) {
  const rank = s => {
    const i = SIZE_ORDER.indexOf(s.trim().toUpperCase());
    if (i >= 0) return i;
    return Number.isNaN(parseFloat(s)) ? 200 : 100;
  };
  const num = s => { const n = parseFloat(s); return Number.isNaN(n) ? Infinity : n; };
  return rank(a) - rank(b) || num(a) - num(b) || a.toLowerCase().localeCompare(b.toLowerCase());
}

/** "Navy / M" for a variant, '' for a single product. */
export const variantLabel = p => [p?.color, p?.size].filter(Boolean).join(' / ');

/** A swatch colour for common colour names; anything unknown gets a neutral grey with its name next to it. */
const SWATCHES = {
  navy: '#1f2a44', black: '#111827', white: '#ffffff', 'heather grey': '#9aa7ae', grey: '#8b949e', gray: '#8b949e',
  red: '#c0262d', blue: '#2563eb', green: '#15803d', olive: '#6b7a2e', yellow: '#facc15', pink: '#ec4899',
  purple: '#7c3aed', orange: '#ea580c', brown: '#7c4a21', beige: '#e7d7b8', teal: '#0f766e', maroon: '#7f1d1d',
};
export const swatch = name => SWATCHES[(name || '').trim().toLowerCase()] || '#c7ced4';

export const slug = s => (s || '').trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

/**
 * The variant to open when the shopper picks a colour or a size: the exact match if it exists, otherwise one in
 * stock with the picked value, otherwise any with the picked value.
 */
export function pickVariant(variants, current, change) {
  const want = { color: current.color ?? null, size: current.size ?? null, ...change };
  const same = v => (v.color ?? null) === want.color && (v.size ?? null) === want.size;
  const key = Object.keys(change)[0];
  const withValue = variants.filter(v => (v[key] ?? null) === change[key]);
  return variants.find(same) || withValue.find(v => v.stock > 0) || withValue[0] || current;
}
