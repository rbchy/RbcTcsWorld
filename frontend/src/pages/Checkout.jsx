import React, { useEffect, useState } from 'react';
import api, { errorText, money } from '../api';
import { go } from '../session';
import { useI18n } from '../i18n';
import { US_STATES } from '../usStates';

const EMPTY_ADDRESS = { fullName: '', line1: '', line2: '', city: '', state: '', zip: '', phone: '' };
const REQUIRED = ['fullName', 'line1', 'city', 'state', 'zip'];

/**
 * Review prices (quote), optionally apply a coupon, enter the delivery address, then place the order.
 * Payment happens on the order page. The address of the customer's last order is filled in for them.
 */
export default function Checkout() {
  const { t } = useI18n();
  const [quote, setQuote] = useState(null);
  const [coupon, setCoupon] = useState('');
  const [applied, setApplied] = useState('');
  const [error, setError] = useState('');
  const [address, setAddress] = useState(EMPTY_ADDRESS);
  const [fieldErrors, setFieldErrors] = useState({});

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
  useEffect(() => {
    api.get('/orders').then(r => {
      const last = r.data.find(o => o.shippingAddress)?.shippingAddress;
      if (last) setAddress(a => (a === EMPTY_ADDRESS ? { ...EMPTY_ADDRESS, ...nullsToEmpty(last) } : a));
    }).catch(() => {});
  }, []);

  const set = field => e => {
    setAddress({ ...address, [field]: e.target.value });
    setFieldErrors({ ...fieldErrors, [field]: undefined });
  };

  const place = async e => {
    e.preventDefault();
    // the same "required" rule as the server, so an empty field is named before anything is sent
    const missing = Object.fromEntries(REQUIRED.filter(f => !address[f].trim()).map(f => [f, t('addrMissing')]));
    if (Object.keys(missing).length) { setFieldErrors(missing); setError(''); return; }
    try {
      const shippingAddress = { ...address, line2: address.line2.trim() || null, phone: address.phone.trim() || null };
      const r = await api.post('/orders', { couponCode: applied || null, shippingAddress });
      go(`/orders/${r.data.id}`);
    } catch (err) {
      // server-side field errors arrive as "shippingAddress.zip": show each one under its own field
      const server = err?.response?.data?.fieldErrors || {};
      setFieldErrors(Object.fromEntries(Object.entries(server)
        .filter(([f]) => f.startsWith('shippingAddress.')).map(([f, m]) => [f.substring('shippingAddress.'.length), m])));
      setError(errorText(err));
    }
  };

  const field = (name, type = 'text', autoComplete = undefined) => (
    <div className="field">
      <label htmlFor={`addr-${name}`}>{t(`addr${name[0].toUpperCase()}${name.slice(1)}`)}</label>
      <input id={`addr-${name}`} data-testid={`addr-${name}`} type={type} value={address[name]} onChange={set(name)}
             autoComplete={autoComplete} aria-invalid={!!fieldErrors[name]}
             aria-describedby={fieldErrors[name] ? `addr-${name}-error` : undefined} />
      {fieldErrors[name] && <span className="field-error" id={`addr-${name}-error`} data-testid={`addr-${name}-error`}>{fieldErrors[name]}</span>}
    </div>
  );

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
          <form onSubmit={place} noValidate data-testid="address-form">
            <fieldset className="address">
              <legend>{t('shipToTitle')}</legend>
              <p className="hint">{t('shipToLead')}</p>
              {field('fullName', 'text', 'name')}
              {field('line1', 'text', 'address-line1')}
              {field('line2', 'text', 'address-line2')}
              {field('city', 'text', 'address-level2')}
              <div className="field">
                <label htmlFor="addr-state">{t('addrState')}</label>
                <select id="addr-state" data-testid="addr-state" value={address.state} onChange={set('state')}
                        autoComplete="address-level1" aria-invalid={!!fieldErrors.state}
                        aria-describedby={fieldErrors.state ? 'addr-state-error' : undefined}>
                  <option value="">{t('addrChooseState')}</option>
                  {US_STATES.map(([code, name]) => <option key={code} value={code}>{code} - {name}</option>)}
                </select>
                {fieldErrors.state && <span className="field-error" id="addr-state-error" data-testid="addr-state-error">{fieldErrors.state}</span>}
              </div>
              {field('zip', 'text', 'postal-code')}
              {field('phone', 'tel', 'tel')}
            </fieldset>
            <button data-testid="place-order" type="submit">Place order</button>
          </form>
        </>
      )}
    </section>
  );
}

function nullsToEmpty(a) {
  return Object.fromEntries(Object.entries(a).map(([k, v]) => [k, v ?? '']));
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
