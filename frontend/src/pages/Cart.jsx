import React, { useEffect, useState } from 'react';
import api, { errorText, money } from '../api';
import { go } from '../session';
import { useI18n } from '../i18n';

export default function Cart() {
  const { t } = useI18n();
  const [cart, setCart] = useState(null);
  const [error, setError] = useState('');

  const load = () => api.get('/cart').then(r => setCart(r.data)).catch(e => setError(errorText(e)));
  useEffect(() => { load(); }, []);

  const update = async (itemId, quantity) => {
    try { setCart((await api.put(`/cart/items/${itemId}`, { quantity: Number(quantity) })).data); setError(''); }
    catch (e) { setError(errorText(e)); load(); }
  };
  const remove = async itemId => {
    try { setCart((await api.delete(`/cart/items/${itemId}`)).data); setError(''); }
    catch (e) { setError(errorText(e)); }
  };

  if (!cart) return <p>Loading cart...</p>;
  return (
    <section className="card">
      <h1>{t('cartTitle')}</h1>
      {error && <p data-testid="cart-error" className="error">{error}</p>}
      {cart.items.length === 0 ? (
        <p data-testid="cart-empty">Your cart is empty. <a href="#/">Continue shopping</a></p>
      ) : (
        <>
          <table className="cart-table" data-testid="cart-table">
            <thead><tr><th>Product</th><th>Price</th><th>Qty</th><th>Total</th><th><span className="sr-only">Actions</span></th></tr></thead>
            <tbody>
              {cart.items.map(i => (
                <tr key={i.itemId} data-testid="cart-line" data-sku={i.sku}>
                  <td>{i.name}{!i.available && <span className="error small" data-testid="line-unavailable"> (no longer available)</span>}</td>
                  <td>{money(i.unitPrice)}</td>
                  <td><input type="number" min="1" max="10" defaultValue={i.quantity} className="qty" data-testid="line-qty" aria-label={`Quantity of ${i.name}`}
                             onBlur={e => e.target.value !== String(i.quantity) && update(i.itemId, e.target.value)} /></td>
                  <td data-testid="line-total">{money(i.lineTotal)}</td>
                  <td><button className="link" data-testid="line-remove" onClick={() => remove(i.itemId)}>Remove</button></td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="total">Subtotal (<span data-testid="cart-count">{cart.totalQuantity}</span> items): <strong data-testid="cart-subtotal">{money(cart.subtotal)}</strong></p>
          <button data-testid="go-checkout" onClick={() => go('/checkout')}>Proceed to checkout</button>
        </>
      )}
    </section>
  );
}
