import React, { useEffect, useState } from 'react';
import api, { errorText, money } from '../api';
import Stars from './Stars';
import { useI18n } from '../i18n';

export default function Wishlist() {
  const { t } = useI18n();
  const [list, setList] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const load = () => api.get('/wishlist').then(r => setList(r.data)).catch(e => setError(errorText(e)));
  useEffect(() => { load(); }, []);

  const act = async (fn, okText) => {
    try { await fn(); setError(''); setNotice(okText); }
    catch (e) { setNotice(''); setError(errorText(e)); }
    load();
  };

  if (!list) return error ? <p data-testid="wishlist-error" className="error">{error}</p> : <p>Loading wishlist...</p>;
  return (
    <section className="card">
      <h1>{t('wishlistTitle')} <span className="muted" data-testid="wishlist-count">{list.count}</span></h1>
      {notice && <p data-testid="wishlist-notice" className="ok">{notice}</p>}
      {error && <p data-testid="wishlist-error" className="error">{error}</p>}
      {list.count === 0 ? <p data-testid="wishlist-empty">Your wishlist is empty. <a href="#/">Browse products</a></p> : (
        <table data-testid="wishlist-table">
          <thead><tr><th>Product</th><th>Rating</th><th>Price</th><th><span className="sr-only">Actions</span></th></tr></thead>
          <tbody>
            {list.items.map(i => (
              <tr key={i.productId} data-testid="wishlist-line" data-sku={i.sku}>
                <td><a href={`#/products/${i.productId}`}>{i.name}</a>
                  {!i.available && <span className="error small" data-testid="wl-unavailable"> (unavailable)</span>}</td>
                <td><Stars average={i.ratingAverage} count={i.ratingCount} testId="wl-rating" /></td>
                <td><span data-testid="wl-price">{money(i.currentPrice)}</span>
                  {Number(i.priceDrop) > 0 && <span className="ok small" data-testid="wl-price-drop"> ↓ {money(i.priceDrop)} cheaper</span>}</td>
                <td className="actions">
                  <button data-testid="wl-move" disabled={!i.available}
                          onClick={() => act(() => api.post(`/wishlist/${i.productId}/move-to-cart`), 'Moved to cart')}>Move to cart</button>
                  <button className="link" data-testid="wl-remove"
                          onClick={() => act(() => api.delete(`/wishlist/${i.productId}`), 'Removed from wishlist')}>Remove</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
