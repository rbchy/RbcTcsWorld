import React, { useEffect, useState } from 'react';
import api, { errorText, money } from '../api';
import { go } from '../session';
import { useI18n } from '../i18n';

/** Review prices (quote), optionally apply a coupon, then place the order. Payment happens on the order page. */
export default function Checkout() {
  const { t } = useI18n();
  const [quote, setQuote] = useState(null);
  const [coupon, setCoupon] = useState('');
  const [applied, setApplied] = useState('');
  const [error, setError] = useState('');

  const loadQuote = async code => {
    try {
      setQuote((await api.post('/checkout/quote', { couponCode: code || null })).data);
      setApplied(code || '');
      setError('');
    } catch (e) {
      setError(errorText(e));
    }
  };
  useEffect(() => { loadQuote(''); }, []);

  const place = async () => {
    try {
      const r = await api.post('/orders', { couponCode: applied || null });
      go(`/orders/${r.data.id}`);
    } catch (e) {
      setError(errorText(e));
    }
  };

  return (
    <section className="card narrow">
      <h1>{t('checkoutTitle')}</h1>
      {error && <p data-testid="checkout-error" className="error">{error}</p>}
      {quote && (
        <>
          <div className="row">
            <input data-testid="coupon-input" placeholder="Coupon code" value={coupon} onChange={e => setCoupon(e.target.value)} />
            <button data-testid="coupon-apply" onClick={() => loadQuote(coupon.trim())}>Apply</button>
          </div>
          <PriceTable q={quote} />
          <button data-testid="place-order" onClick={place}>Place order</button>
        </>
      )}
    </section>
  );
}

export function PriceTable({ q }) {
  return (
    <table className="prices" data-testid="price-table">
      <tbody>
        <tr><td>Subtotal</td><td data-testid="price-subtotal">{money(q.subtotal)}</td></tr>
        <tr><td>Discount{q.couponCode ? ` (${q.couponCode})` : ''}</td><td data-testid="price-discount">-{money(q.discount)}</td></tr>
        <tr><td>Shipping</td><td data-testid="price-shipping">{Number(q.shippingFee) === 0 ? 'FREE' : money(q.shippingFee)}</td></tr>
        <tr><td>Tax (6%)</td><td data-testid="price-tax">{money(q.tax)}</td></tr>
        <tr className="grand"><td>Total</td><td data-testid="price-total">{money(q.total)}</td></tr>
      </tbody>
    </table>
  );
}
