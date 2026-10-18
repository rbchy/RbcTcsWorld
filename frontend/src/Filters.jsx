import React from 'react';
import { useI18n } from './i18n';

/** Empty selection of the catalog's filter panel. */
export const NO_FILTERS = { categories: [], price: '', rating: 0, inStock: false, colors: [], sizes: [] };

export const isFiltered = f => f.categories.length > 0 || f.price !== '' || f.rating > 0 || f.inStock || f.colors.length > 0 || f.sizes.length > 0;

/** The filter selection as query parameters of GET /api/products and /api/products/facets. */
export function filterParams(f, facets) {
  const p = {};
  if (f.categories.length) p.category = f.categories.join(',');
  const band = facets?.prices?.find(b => b.key === f.price);
  if (band) { p.minPrice = band.min; if (band.max != null) p.maxPrice = band.max; }
  if (f.rating > 0) p.minRating = f.rating;
  if (f.inStock) p.inStock = true;
  if (f.colors.length) p.colors = f.colors.join(',');
  if (f.sizes.length) p.sizes = f.sizes.join(',');
  return p;
}

const toggle = (list, v) => (list.includes(v) ? list.filter(x => x !== v) : [...list, v]);

/**
 * Check-box filters with a count behind every option, like Amazon's left column. The counts come from
 * /api/products/facets and ignore the facet's own selection, so ticking "books" still shows how many "home" items exist.
 */
export default function Filters({ value, onChange, facets }) {
  const { t } = useI18n();
  const f = value;
  const set = patch => onChange({ ...f, ...patch });
  const money = n => `$${Number(n).toFixed(0)}`;
  const bandLabel = b => (b.max == null ? t('priceOver', { p: money(b.min) }) : Number(b.min) === 0 ? t('priceUnder', { p: money(Number(b.max) + 0.01) }) : `${money(b.min)} - ${money(Math.ceil(Number(b.max)))}`);
  const group = (id, title, children) => (
    <fieldset className="facet" data-testid={`facet-${id}`}><legend>{title}</legend>{children}</fieldset>
  );
  const boxes = (id, options, selected, key) => options.map(o => (
    <label key={o.value} className="facet-opt">
      <input type="checkbox" data-testid={`filter-${id}`} data-value={o.value} checked={selected.includes(o.value)}
             onChange={() => set({ [key]: toggle(selected, o.value) })} />
      <span>{o.value}</span><em data-testid="facet-count">{o.count}</em>
    </label>
  ));
  return (
    <details className="filters" open data-testid="filters">
      <summary>{t('filters')}</summary>
      {isFiltered(f) && <button type="button" className="ghost small" data-testid="filters-clear" onClick={() => onChange(NO_FILTERS)}>{t('clearFilters')}</button>}
      {!facets && <p className="muted small">…</p>}
      {facets && (
        <>
          {facets.categories.length > 0 && group('category', t('filterCategory'), boxes('category', facets.categories, f.categories, 'categories'))}
          {group('price', t('filterPrice'), facets.prices.map(b => (
            <label key={b.key} className="facet-opt">
              <input type="radio" name="price" data-testid="filter-price" data-value={b.key} checked={f.price === b.key} disabled={b.count === 0 && f.price !== b.key}
                     onChange={() => set({ price: f.price === b.key ? '' : b.key })} onClick={() => f.price === b.key && set({ price: '' })} />
              <span>{bandLabel(b)}</span><em data-testid="facet-count">{b.count}</em>
            </label>
          )))}
          {group('rating', t('filterRating'), facets.ratings.map(r => (
            <label key={r.min} className="facet-opt">
              <input type="radio" name="rating" data-testid="filter-rating" data-value={r.min} checked={f.rating === r.min} disabled={r.count === 0 && f.rating !== r.min}
                     onChange={() => set({ rating: r.min })} onClick={() => f.rating === r.min && set({ rating: 0 })} />
              <span><span aria-hidden="true" className="stars-mini">{'★'.repeat(r.min)}{'☆'.repeat(5 - r.min)}</span> {t('andUp')}</span><em data-testid="facet-count">{r.count}</em>
            </label>
          )))}
          {group('stock', t('filterAvailability'), (
            <label className="facet-opt">
              <input type="checkbox" data-testid="filter-instock" checked={f.inStock} onChange={() => set({ inStock: !f.inStock })} />
              <span>{t('inStockOnly')}</span><em data-testid="facet-count">{facets.inStock}</em>
            </label>
          ))}
          {facets.colors.length > 0 && group('color', t('filterColor'), boxes('color', facets.colors, f.colors, 'colors'))}
          {facets.sizes.length > 0 && group('size', t('filterSize'), boxes('size', facets.sizes, f.sizes, 'sizes'))}
        </>
      )}
    </details>
  );
}
