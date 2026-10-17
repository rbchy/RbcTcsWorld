import React, { useEffect, useState } from 'react';
import api, { errorText, money } from '../api';
import { getSession, go } from '../session';
import Stars from './Stars';
import ProductImage from '../ProductImage';
import { useI18n } from '../i18n';
import { Icon } from '../icons';
import { swatch } from '../variants';

const PAGE_SIZE = 20;

export default function Home() {
  const { t } = useI18n();
  const [products, setProducts] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [q, setQ] = useState('');
  const [error, setError] = useState('');
  const [flash, setFlash] = useState('');
  const [options, setOptions] = useState({});      // variant group -> {options, colors, sizes, minPrice, maxPrice}

  // The catalog is paginated (DEF-007): 20 products per request, "Load more" fetches the next page.
  // isCurrent: DEF-016 - every keystroke in the search box sends a request, and responses can arrive out of
  // order. A late answer for "mo" must never replace the results for "mouse", so stale answers are dropped.
  const load = (pageNo, append, isCurrent = () => true) =>
    api.get('/products', { params: { q, page: pageNo, size: PAGE_SIZE, grouped: true } })
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

  // Short debounce (fewer requests while typing) + a guard so only the newest search may update the list.
  useEffect(() => {
    let current = true;
    const timer = setTimeout(() => load(0, false, () => current), q ? 200 : 0);
    return () => { current = false; clearTimeout(timer); };
  }, [q]);

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
        <h1>RbcTcsWorld E-Commerce</h1>
        <input data-testid="search-input" type="search" aria-label={t('searchProducts')} placeholder={t('searchProducts')} value={q} onChange={e => setQ(e.target.value)} />
      </header>
      {!q && <Banner t={t} />}
      {error && <p data-testid="error-banner" className="error">{error}</p>}
      {flash && <p data-testid="flash" className="ok">{flash}</p>}
      <p className="muted small" id="catalog" data-testid="catalog-total">{t('showing', { n: products.length, total })}</p>
      {!error && total === 0 && q && <p className="card muted" data-testid="no-results">{t('noProducts')}</p>}
      <section className="grid" data-testid="product-grid">
        {products.map(p => <ProductCard key={p.id} p={p} options={options[p.variantGroup]} onAdd={add} onWish={wish} t={t} />)}
      </section>
      {products.length < total && (
        <p className="center"><button className="ghost" data-testid="load-more" onClick={() => load(page + 1, true)}>{t('loadMore')}</button></p>
      )}
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
      <p><strong data-testid="product-price">{money(p.price)}</strong></p>
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

/** The one bold element of the storefront: a parcel label carrying the welcome offer. */
function Banner({ t }) {
  return (
    <section className="banner" aria-labelledby="banner-title" data-testid="banner">
      <div className="label-card">
        <div className="barcode" aria-hidden="true" />
        <div className="label-body">
          <h2 id="banner-title">{t('bannerTitle')}</h2>
          <p>{t('bannerText')}</p>
          <p className="coupon"><span>{t('bannerCode')}</span> <strong data-testid="banner-code">WELCOME10</strong></p>
        </div>
      </div>
      <a className="banner-link" href="#/" onClick={e => { e.preventDefault(); document.getElementById('catalog')?.scrollIntoView({ behavior: 'smooth' }); }}>{t('bannerShop')}</a>
    </section>
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
