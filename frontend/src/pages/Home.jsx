import React, { useEffect, useState } from 'react';
import api, { errorText, money } from '../api';
import { getSession, go } from '../session';
import Stars from './Stars';
import ProductImage from '../ProductImage';
import { useI18n } from '../i18n';
import { Icon } from '../icons';
import { swatch } from '../variants';
import { Price, Countdown, isLive } from '../Deal';
import Filters, { NO_FILTERS, filterParams, isFiltered } from '../Filters';
import Watermark from '../Watermark';
import ShopScene from '../ShopScene';

const PAGE_SIZE = 20;

export default function Home() {
  const { t } = useI18n();
  const [products, setProducts] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [q, setQ] = useState('');
  const [error, setError] = useState('');
  const [flash, setFlash] = useState('');
  const [deals, setDeals] = useState([]);           // flash deals running now
  const [filters, setFilters] = useState(NO_FILTERS);
  const [facets, setFacets] = useState(null);
  const [options, setOptions] = useState({});      // variant group -> {options, colors, sizes, minPrice, maxPrice}

  // The catalog is paginated (DEF-007): 20 products per request, "Load more" fetches the next page.
  // isCurrent: DEF-016 - every keystroke in the search box sends a request, and responses can arrive out of
  // order. A late answer for "mo" must never replace the results for "mouse", so stale answers are dropped.
  const load = (pageNo, append, isCurrent = () => true) =>
    api.get('/products', { params: { q, page: pageNo, size: PAGE_SIZE, grouped: true, ...filterParams(filters, facets) } })
      .then(r => {
        if (!isCurrent()) return;
        setProducts(prev => (append ? [...prev, ...r.data] : r.data));
        // one card per item: ask once for the options of every item with sizes or colours on this page
        const groups = [...new Set(r.data.map(p => p.variantGroup).filter(Boolean))];
        if (groups.length) {
          api.get('/products/variant-groups', { params: { names: groups.join(',') } })
            .then(o => setOptions(prev => ({ ...prev, ...Object.fromEntries(o.data.map(g => [g.group, g])) })))
            .catch(() => {});
        }
        setTotal(Number(r.headers['x-total-count'] ?? r.data.length));
        setPage(pageNo);
        setError('');
      })
      .catch(e => { if (!isCurrent()) return; if (!append) setProducts([]); setError(errorText(e)); });

  // The counts behind every filter option. They follow the search words and the other selected filters.
  useEffect(() => {
    let current = true;
    api.get('/products/facets', { params: { q, ...filterParams(filters, facets) } })
      .then(r => { if (current) setFacets(r.data); })
      .catch(() => {});
    return () => { current = false; };
  }, [q, filters]);

  // Short debounce (fewer requests while typing) + a guard so only the newest search may update the list.
  useEffect(() => {
    let current = true;
    const timer = setTimeout(() => load(0, false, () => current), q ? 200 : 0);
    return () => { current = false; clearTimeout(timer); };
  }, [q, filters]);

  useEffect(() => { api.get('/products/deals').then(r => setDeals(r.data)).catch(() => {}); }, []);

  const add = async (productId, quantity) => {
    if (!getSession()) { go('/login'); return; }
    try {
      const r = await api.post('/cart/items', { productId, quantity: Number(quantity) });
      setFlash(`Added to cart (${r.data.totalQuantity} item(s) in cart)`);
      setError('');
    } catch (e) {
      setFlash('');
      setError(errorText(e));
    }
  };

  const wish = async productId => {
    if (!getSession()) { go('/login'); return; }
    try {
      const r = await api.post('/wishlist', { productId });
      setFlash(r.status === 201 ? `Saved to wishlist (${r.data.count})` : 'Already on your wishlist');
      setError('');
    } catch (e) {
      setFlash('');
      setError(errorText(e));
    }
  };

  return (
    <>
      <header className="page-head">
        <h1>RbcTcsWorld Shop</h1>
        <input data-testid="search-input" type="search" aria-label={t('searchProducts')} placeholder={t('searchProducts')} value={q} onChange={e => setQ(e.target.value)} />
      </header>
      {!q && !isFiltered(filters) && <Banner t={t} />}
      {facets?.categories?.length > 0 && <CategoryRow t={t} categories={facets.categories} filters={filters} onChange={setFilters} />}
      {error && <p data-testid="error-banner" className="error">{error}</p>}
      {flash && <p data-testid="flash" className="ok">{flash}</p>}
      <p className="muted small" id="catalog" data-testid="catalog-total">{t('showing', { n: products.length, total })}</p>
      {!error && total === 0 && (q || isFiltered(filters)) && <p className="card muted" data-testid="no-results">{t('noProducts')}</p>}
      {deals.length > 0 && !q && !isFiltered(filters) && (
        <section className="deals-shelf" data-testid="deals-shelf" aria-labelledby="deals-title">
          <h2 id="deals-title">{t('flashDeals')}</h2>
          <div className="deals-row">
            {deals.map(d => (
              <article className="product-card" key={d.id} data-testid="deal-card" data-sku={d.sku}>
                <ProductImage product={d} />
                <h3><a href={`#/products/${d.id}`}>{d.name}</a></h3>
                <p><Price p={d} testId="deal-card-price" /></p>
                <Countdown endsAt={d.dealEndsAt} testId="deal-card-countdown" />
              </article>
            ))}
          </div>
        </section>
      )}
      <div className="catalog-layout">
        <aside aria-label={t('filters')}><Filters value={filters} onChange={setFilters} facets={facets} /></aside>
        <div>
      <section className="grid" data-testid="product-grid">
        {products.map(p => <ProductCard key={p.id} p={p} options={options[p.variantGroup]} onAdd={add} onWish={wish} t={t} />)}
      </section>
      {products.length < total && (
        <p className="center"><button className="ghost" data-testid="load-more" onClick={() => load(page + 1, true)}>{t('loadMore')}</button></p>
      )}
        </div>
      </div>
    </>
  );
}

function ProductCard({ p, options, onAdd, onWish, t }) {
  const [qty, setQty] = useState(1);
  if (p.variantGroup) return <VariantCard p={p} options={options} t={t} />;
  return (
    <article className="product-card" data-testid="product-card" data-sku={p.sku}>
      <ProductImage product={p} />
      <h2><a href={`#/products/${p.id}`} data-testid="product-name">{p.name}</a></h2>
      <p className="muted">{p.category}</p>
      <Stars average={p.ratingAverage} count={p.ratingCount} />
      <p><Price p={p} /></p>
      {isLive(p) && <Countdown endsAt={p.dealEndsAt} />}
      <p className="muted" data-testid="product-stock">{t('stock')}: {p.stock}</p>
      <div className="row">
        <input type="number" min="1" max="10" value={qty} data-testid="qty-input" aria-label={`Quantity of ${p.name}`}
               onChange={e => setQty(e.target.value)} className="qty" />
        <button data-testid="add-to-cart" disabled={p.stock < 1} onClick={() => onAdd(p.id, qty)}>
          {p.stock < 1 ? t('outOfStock') : t('addToCart')}
        </button>
        <button className="ghost" data-testid="add-to-wishlist" title={t('saveWishlist')} aria-label={`${t('saveWishlist')}: ${p.name}`} onClick={() => onWish(p.id)}><Icon name="heart" /></button>
      </div>
    </article>
  );
}

/**
 * The startup banner: the welcome offer with its code on a coupon ticket, a way in (log in / create account) for
 * visitors, a delivery picture (the van on its way to a home) and the RbcTcsWorld name as a watermark behind the text.
 */
function Banner({ t }) {
  const session = getSession();
  const visitor = !session || session.guest;
  const toCatalog = e => { e.preventDefault(); document.getElementById('catalog')?.scrollIntoView({ behavior: 'smooth' }); };
  return (
    <section className="banner" aria-labelledby="banner-title" data-testid="banner">
      <Watermark tone="bigLight" />
      <div className="banner-main">
        <p className="banner-kicker">{t('bannerKicker')}</p>
        <h2 id="banner-title">{t('bannerTitle')}</h2>
        <p className="banner-text">{t('bannerText')}</p>
        <p className="coupon"><span>{t('bannerCode')}</span> <strong data-testid="banner-code">WELCOME10</strong></p>
        <div className="banner-actions">
          {visitor && <a className="banner-btn" href="#/login" data-testid="banner-login">{t('navLogin')}</a>}
          {visitor && <a className="banner-btn ghost" href="#/register" data-testid="banner-register">{t('navRegister')}</a>}
          <a className="banner-link" href="#/" onClick={toCatalog} data-testid="banner-shop">{t('bannerShop')}</a>
        </div>
      </div>
      <ShopScene />
    </section>
  );
}

/** One picture per category for the "Shop by category" row; anything else gets the shopping bag. */
const CATEGORY_ICONS = { electronics: 'power', home: 'warehouse', books: 'receipt', clothing: 'tag' };

/**
 * Shop by category: one tile per category, like the department row of a big store. A tile selects only that
 * category (the same filter as the check-box in the filter panel); "All products" clears it again.
 */
function CategoryRow({ t, categories, filters, onChange }) {
  const only = filters.categories.length === 1 ? filters.categories[0] : null;
  const pick = value => onChange({ ...filters, categories: value && only !== value ? [value] : [] });
  return (
    <nav className="category-row" aria-labelledby="category-row-title" data-testid="category-row">
      <h2 id="category-row-title">{t('shopByCategory')}</h2>
      <ul>
        <li>
          <button type="button" className="cat-tile" aria-pressed={filters.categories.length === 0}
                  data-testid="category-chip" data-value="" onClick={() => pick(null)}>
            <span className="cat-icon" aria-hidden="true"><Icon name="grid" /></span>
            <span className="cat-name">{t('allProducts')}</span>
          </button>
        </li>
        {categories.map((c, i) => (
          <li key={c.value}>
            <button type="button" className={`cat-tile tone-${i % 4}`} aria-pressed={only === c.value}
                    data-testid="category-chip" data-value={c.value} onClick={() => pick(c.value)}>
              <span className="cat-icon" aria-hidden="true"><Icon name={CATEGORY_ICONS[c.value.toLowerCase()] || 'bag'} /></span>
              <span className="cat-name">{c.value}</span>
              <span className="cat-count">{c.count}</span>
            </button>
          </li>
        ))}
      </ul>
    </nav>
  );
}

/**
 * An item with sizes or colours: one card for all of them, like Amazon or Shein. The size must be chosen on the
 * product page, so the card leads there instead of adding to the cart.
 */
function VariantCard({ p, options, t }) {
  const range = options && Number(options.minPrice) !== Number(options.maxPrice);
  return (
    <article className="product-card variant-card" data-testid="product-card" data-sku={p.sku} data-group={p.variantGroup}>
      <ProductImage product={p} />
      <h2><a href={`#/products/${p.id}`} data-testid="product-name">{p.name}</a></h2>
      <p className="muted">{p.category}</p>
      <Stars average={p.ratingAverage} count={p.ratingCount} />
      <p><strong data-testid="product-price">{range ? t('fromPrice', { p: money(options.minPrice) }) : money(p.price)}</strong></p>
      {options && (
        <div className="card-options" data-testid="card-options">
          {options.colors.length > 0 && (
            <span className="mini-swatches" aria-hidden="true">
              {options.colors.map(c => <span key={c} className="mini-dot" style={{ background: swatch(c) }} title={c} />)}
            </span>
          )}
          <span>{t('optionsSummary', { n: options.options, c: options.colors.length, s: options.sizes.length })}</span>
          {options.sizes.length > 0 && <span className="muted small size-span">{options.sizes.join(' ')}</span>}
        </div>
      )}
      <div className="row">
        <a className="button-link choose" href={`#/products/${p.id}`} data-testid="choose-options">{t('chooseOptions')}</a>
      </div>
    </article>
  );
}
