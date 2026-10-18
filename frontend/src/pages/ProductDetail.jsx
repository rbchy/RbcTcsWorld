import React, { useEffect, useState } from 'react';
import api, { errorText, money } from '../api';
import { getSession, go } from '../session';
import Stars from './Stars';
import ProductImage from '../ProductImage';
import { useI18n } from '../i18n';
import { compareSizes, pickVariant, slug, swatch, variantLabel } from '../variants';

/** Product page: rating summary, reviews (sortable) and the review form for verified buyers. */
export default function ProductDetail({ id }) {
  const [product, setProduct] = useState(null);
  const [data, setData] = useState(null);
  const [sort, setSort] = useState('newest');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [similar, setSimilar] = useState([]);
  const [variants, setVariants] = useState([]);
  const [qty, setQty] = useState('1');
  const [cartMsg, setCartMsg] = useState('');
  const { t } = useI18n();

  useEffect(() => {
    setCartMsg(''); setQty('1');
    api.get(`/products/${id}/variants`).then(r => setVariants(r.data)).catch(() => setVariants([]));
  }, [id]);

  const qtyError = !/^\d+$/.test(qty.trim()) || Number(qty) < 1 || Number(qty) > 10 ? t('vQty') : '';
  const addToCart = async () => {
    if (!getSession()) { go('/login'); return; }
    if (qtyError) return;
    try {
      const r = await api.post('/cart/items', { productId: Number(id), quantity: Number(qty) });
      setError(''); setCartMsg(t('addedToCart', { n: r.data.totalQuantity }));
    } catch (e) { setCartMsg(''); setError(errorText(e)); }
  };

  useEffect(() => {
    setSimilar([]);
    api.get(`/products/${id}/similar`, { params: { limit: 4 } }).then(r => setSimilar(r.data)).catch(() => setSimilar([]));
  }, [id]);

  const load = async () => {
    try {
      const [p, r] = await Promise.all([api.get(`/products/${id}`), api.get(`/products/${id}/reviews`, { params: { sort } })]);
      setProduct(p.data); setData(r.data);
    } catch (e) {
      setError(errorText(e));
    }
  };
  useEffect(() => { load(); }, [id, sort]);

  const wish = async () => {
    if (!getSession()) { go('/login'); return; }
    try {
      const r = await api.post('/wishlist', { productId: Number(id) });
      setError(''); setNotice(r.status === 201 ? 'Saved to wishlist' : 'Already on your wishlist');
    } catch (e) { setNotice(''); setError(errorText(e)); }
  };

  if (!product || !data) return error ? <p data-testid="review-error" className="error">{error}</p> : <p>Loading product...</p>;
  return (
    <section className="card">
      <div className="detail-head">
        <ProductImage product={product} alt={product.name} className="detail-image" testId="detail-image" eager />
        <div>
          <h1 data-testid="detail-name">{product.name}</h1>
          <p className="muted">{product.category} · SKU <span data-testid="detail-sku">{product.sku}</span></p>
          <p className="detail-price-row"><strong data-testid="detail-price">{money(product.price)}</strong>
            <span className={`stock-note ${product.stock === 0 ? 'out' : product.stock <= 5 ? 'low' : 'in'}`} data-testid="detail-stock">
              {product.stock === 0 ? t('outOfStock') : product.stock <= 5 ? t('onlyLeft', { n: product.stock }) : t('inStock')}
            </span>
          </p>
          {variants.length > 1 && <VariantPicker product={product} variants={variants} t={t} />}
          <div className="buy-box">
            <div className="field qty-field">
              <label htmlFor="detail-qty">{t('quantity')}</label>
              <input id="detail-qty" className="qty" type="number" min="1" max="10" value={qty} onChange={e => setQty(e.target.value)}
                     aria-invalid={!!qtyError} aria-describedby={qtyError ? 'detail-qty-error' : undefined} data-testid="detail-qty" />
              {qtyError && <span className="field-error" id="detail-qty-error" data-testid="detail-qty-error">{qtyError}</span>}
            </div>
            <button data-testid="detail-add-to-cart" disabled={product.stock < 1 || !!qtyError} onClick={addToCart}>
              {product.stock < 1 ? t('outOfStock') : t('addToCart')}
            </button>
            <button className="ghost" data-testid="detail-wishlist" onClick={wish}>Save to wishlist</button>
          </div>
          {cartMsg && <p className="ok" role="status" data-testid="detail-cart-notice">{cartMsg} <a href="#/cart">{t('navCart')}</a></p>}
        </div>
      </div>
      {notice && <p data-testid="review-notice" className="ok">{notice}</p>}
      {error && <p data-testid="review-error" className="error">{error}</p>}

      <div className="two-col">
        <div>
          <h2>Customer reviews</h2>
          <p className="big-rating">
            <Stars average={data.averageRating} count={data.reviewCount} testId="avg-rating" />
          </p>
          <table className="dist" data-testid="distribution">
            <tbody>
              {[5, 4, 3, 2, 1].map(star => {
                const n = data.distribution[star] ?? 0;
                const pct = data.reviewCount ? Math.round((n * 100) / data.reviewCount) : 0;
                return (
                  <tr key={star} data-testid={`dist-${star}`} data-count={n}>
                    <td>{star} ★</td>
                    <td className="bar"><span style={{ width: `${pct}%` }} /></td>
                    <td className="muted">{n}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          {getSession() ? <ReviewForm productId={Number(id)} onDone={msg => { setError(''); setNotice(msg); load(); }}
                                      onError={msg => { setNotice(''); setError(msg); }} />
                        : <p className="muted"><a href="#/login" data-testid="review-login">Log in</a> to review a product you received.</p>}
        </div>
        <div>
          <div className="row between">
            <h2>{data.reviewCount} review(s)</h2>
            <select data-testid="review-sort" aria-label="Sort reviews" value={sort} onChange={e => setSort(e.target.value)}>
              <option value="newest">Newest</option>
              <option value="highest">Highest rating</option>
              <option value="lowest">Lowest rating</option>
            </select>
          </div>
          {data.reviews.length === 0 && <p data-testid="reviews-empty" className="muted">No reviews yet.</p>}
          <ul className="reviews" data-testid="reviews">
            {data.reviews.map(r => (
              <li key={r.id} data-testid="review-item" data-rating={r.rating}>
                <Stars average={r.rating} count={null} testId="review-stars" />
                {r.title && <strong data-testid="review-title"> {r.title}</strong>}
                {r.body && <p data-testid="review-body">{r.body}</p>}
                <p className="muted small"><span data-testid="review-reviewer">{r.reviewer}</span> · Verified purchase · {new Date(r.createdAt).toLocaleDateString()}</p>
              </li>
            ))}
          </ul>
        </div>
      </div>
      {similar.length > 0 && (
        <section className="similar" aria-labelledby="similar-title" data-testid="similar-products">
          <h2 id="similar-title">{t('similarTitle')}</h2>
          <ul className="similar-grid">
            {similar.map(sp => (
              <li key={sp.id} data-testid="similar-product">
                <a href={`#/products/${sp.id}`} className="similar-card">
                  <ProductImage product={sp} alt="" className="similar-image" testId="similar-image" />
                  <span className="similar-name">{sp.name}</span>
                  <span className="similar-meta"><strong>{money(sp.price)}</strong>
                    {sp.stock > 0 ? '' : <em className="muted"> · {t('soldOut')}</em>}</span>
                </a>
              </li>
            ))}
          </ul>
        </section>
      )}
    </section>
  );
}

function ReviewForm({ productId, onDone, onError }) {
  const [rating, setRating] = useState('5');
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const submit = async e => {
    e.preventDefault();
    try {
      await api.post('/reviews', { productId, rating: Number(rating), title, body });
      setTitle(''); setBody('');
      onDone('Thank you! Your review is published.');
    } catch (err) {
      onError(errorText(err));
    }
  };
  return (
    <form data-testid="review-form" onSubmit={submit}>
      <h2>Write a review</h2>
      <label>Rating
        <select data-testid="review-rating" value={rating} onChange={e => setRating(e.target.value)}>
          {[5, 4, 3, 2, 1].map(n => <option key={n} value={n}>{n} star{n > 1 ? 's' : ''}</option>)}
        </select>
      </label>
      <label>Title<input data-testid="review-title-input" maxLength={100} value={title} onChange={e => setTitle(e.target.value)} /></label>
      <label>Review<textarea data-testid="review-body-input" rows={4} maxLength={2000} value={body} onChange={e => setBody(e.target.value)} /></label>
      <button data-testid="review-submit" type="submit">Submit review</button>
      <p className="muted small">Only customers who received this product can review it.</p>
    </form>
  );
}

/**
 * Colour swatches and size buttons. Each size and colour is its own product (own SKU, price, stock), so picking
 * one opens that product: price, stock, picture and reviews follow. Sizes that do not exist in the chosen colour
 * cannot be picked; sold-out ones can be looked at but not bought.
 */
function VariantPicker({ product, variants, t }) {
  const colors = [...new Set(variants.map(v => v.color).filter(Boolean))];
  const sizes = [...new Set(variants.map(v => v.size).filter(Boolean))].sort(compareSizes);
  const open = v => { if (v && v.id !== product.id) go(`/products/${v.id}`); };
  return (
    <div className="variant-picker" data-testid="variant-picker">
      <p className="picked" data-testid="variant-selected">{t('selectedOption')}: <strong>{variantLabel(product)}</strong></p>
      {colors.length > 0 && (
        <fieldset className="opt-group">
          <legend>{t('colorLabel')}: <span>{product.color}</span></legend>
          <div className="swatches">
            {colors.map(c => {
              const all = variants.filter(v => v.color === c);
              const soldOut = all.every(v => v.stock === 0);
              return (
                <button key={c} type="button" className={`swatch${soldOut ? ' sold-out' : ''}`} aria-pressed={product.color === c}
                        data-testid={`pick-color-${slug(c)}`} onClick={() => open(pickVariant(variants, product, { color: c }))}
                        title={soldOut ? `${c}: ${t('soldOut')}` : c}>
                  <span className="dot" style={{ background: swatch(c) }} aria-hidden="true" />
                  <span>{c}</span>{soldOut && <span className="sr-only">, {t('soldOut')}</span>}
                </button>
              );
            })}
          </div>
        </fieldset>
      )}
      {sizes.length > 0 && (
        <fieldset className="opt-group">
          <legend>{t('sizeLabel')}: <span>{product.size}</span></legend>
          <div className="size-row">
            {sizes.map(s => {
              const v = variants.find(x => x.size === s && (x.color ?? null) === (product.color ?? null));
              return (
                <button key={s} type="button" className={`size-btn${v && v.stock === 0 ? ' sold-out' : ''}`} disabled={!v}
                        aria-pressed={product.size === s} data-testid={`pick-size-${slug(s)}`} onClick={() => open(v)}
                        title={!v ? t('notInColour') : v.stock === 0 ? t('soldOut') : s}>
                  {s}{v && v.stock === 0 && <span className="sr-only">, {t('soldOut')}</span>}
                </button>
              );
            })}
          </div>
        </fieldset>
      )}
    </div>
  );
}
