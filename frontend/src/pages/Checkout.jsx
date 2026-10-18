import React, { useEffect, useState } from 'react';
import api, { errorText, money } from '../api';
import { go } from '../session';
import { useI18n } from '../i18n';
import { getSession } from '../session';
import AddressFields, { EMPTY_ADDRESS, addressBody, nullsToEmpty } from '../AddressFields';

const REQUIRED = ['fullName', 'line1', 'city', 'state', 'zip'];
const same = (a, b) => ['fullName', 'line1', 'line2', 'city', 'state', 'zip']
  .every(k => (a[k] || '').trim().toLowerCase() === (b[k] || '').trim().toLowerCase());

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
  const [saved, setSaved] = useState([]);          // the address book
  const [chosen, setChosen] = useState('new');     // id of a saved address, or 'new'
  const [keep, setKeep] = useState(false);         // save the typed address to the address book
  const guest = getSession()?.guest;

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
  // the default saved address first; without an address book, the address of the last order
  useEffect(() => {
    api.get('/addresses').then(r => {
      setSaved(r.data);
      const first = r.data.find(a => a.isDefault) || r.data[0];
      if (first) { setChosen(first.id); setAddress(nullsToEmpty(first.address)); return; }
      api.get('/orders').then(o => {
        const last = o.data.find(x => x.shippingAddress)?.shippingAddress;
        if (last) setAddress(a => (a === EMPTY_ADDRESS ? nullsToEmpty(last) : a));
      }).catch(() => {});
    }).catch(() => {});
  }, []);

  const change = (field, value) => {
    setAddress({ ...address, [field]: value });
    setFieldErrors({ ...fieldErrors, [field]: undefined });
  };
  const choose = a => {
    setChosen(a === 'new' ? 'new' : a.id);
    setAddress(a === 'new' ? EMPTY_ADDRESS : nullsToEmpty(a.address));
    setFieldErrors({});
  };
  const alreadySaved = saved.some(a => same(a.address, address));

  const place = async e => {
    e.preventDefault();
    // the same "required" rule as the server, so an empty field is named before anything is sent
    const missing = Object.fromEntries(REQUIRED.filter(f => !address[f].trim()).map(f => [f, t('addrMissing')]));
    if (Object.keys(missing).length) { setFieldErrors(missing); setError(''); return; }
    try {
      const shippingAddress = { ...address, line2: address.line2.trim() || null, phone: address.phone.trim() || null };
      const r = await api.post('/orders', { couponCode: applied || null, shippingAddress });
      if (keep && !alreadySaved) {
        await api.post('/addresses', { address: addressBody(address), makeDefault: saved.length === 0 }).catch(() => {});
      }
      go(`/orders/${r.data.id}`);
    } catch (err) {
      // server-side field errors arrive as "shippingAddress.zip": show each one under its own field
      const server = err?.response?.data?.fieldErrors || {};
      setFieldErrors(Object.fromEntries(Object.entries(server)
        .filter(([f]) => f.startsWith('shippingAddress.')).map(([f, m]) => [f.substring('shippingAddress.'.length), m])));
      setError(errorText(err));
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
          <form onSubmit={place} noValidate data-testid="address-form">
            {saved.length > 0 && (
              <fieldset className="address saved-addresses" data-testid="saved-addresses">
                <legend>{t('deliverTo')}</legend>
                {saved.map(a => (
                  <label key={a.id} className={`addr-choice${chosen === a.id ? ' on' : ''}`} data-testid="saved-address">
                    <input type="radio" name="deliver-to" checked={chosen === a.id} onChange={() => choose(a)} />
                    <span><strong>{a.label || a.address.fullName}</strong>{a.isDefault && <em className="badge state-active">{t('defaultBadge')}</em>}
                      <span className="small muted">{[a.address.line1, a.address.city, `${a.address.state} ${a.address.zip}`].join(', ')}</span></span>
                  </label>
                ))}
                <label className={`addr-choice${chosen === 'new' ? ' on' : ''}`}>
                  <input type="radio" name="deliver-to" checked={chosen === 'new'} onChange={() => choose('new')} data-testid="new-address" />
                  <span><strong>{t('useNewAddress')}</strong></span>
                </label>
              </fieldset>
            )}
            <fieldset className="address">
              <legend>{t('shipToTitle')}</legend>
              <p className="hint">{t('shipToLead')}</p>
              <AddressFields prefix="addr" value={address} onChange={change} errors={fieldErrors} t={t} />
              {!guest && !alreadySaved && (
                <label className="check">
                  <input type="checkbox" checked={keep} onChange={e => setKeep(e.target.checked)} data-testid="save-address" />
                  {t('saveAddress')}
                </label>
              )}
            </fieldset>
            <button data-testid="place-order" type="submit">Place order</button>
            {guest && <p className="hint" data-testid="guest-hint">{t('guestHint')} <a href="#/forgot-password">{t('forgotLink')}</a></p>}
          </form>
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
