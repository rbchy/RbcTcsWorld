import React, { useEffect, useState } from 'react';
import api, { errorText, money } from '../api';
import { PriceTable } from './Checkout';
import { addressLine } from '../usStates';
import { useI18n } from '../i18n';

/** One order: price breakdown, payment form, cancel, return request, payments and tracking timeline. */
export default function OrderDetail({ id }) {
  const { t } = useI18n();
  const [order, setOrder] = useState(null);
  const [tracking, setTracking] = useState(null);
  const [payments, setPayments] = useState([]);
  const [ret, setRet] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const load = async () => {
    try {
      const [o, t, p] = await Promise.all([
        api.get(`/orders/${id}`), api.get(`/orders/${id}/tracking`), api.get(`/orders/${id}/payments`)]);
      setOrder(o.data); setTracking(t.data); setPayments(p.data);
      api.get(`/orders/${id}/return`).then(r => setRet(r.data)).catch(() => setRet(null));
    } catch (e) {
      setError(errorText(e));
    }
  };
  useEffect(() => { load(); }, [id]);

  // The invoice needs the login token, so it is fetched with it and handed to the browser as a file.
  const downloadInvoice = async () => {
    try {
      const r = await api.get(`/orders/${id}/invoice`, { responseType: 'blob' });
      const url = URL.createObjectURL(r.data);
      const a = document.createElement('a');
      a.href = url; a.download = `invoice-${order.orderNumber}.pdf`;
      document.body.appendChild(a); a.click(); a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      setError(''); setNotice(t('invoiceDownloaded'));
    } catch (e) { setNotice(''); setError(errorText(e)); }
  };

  const act = async (fn, okText) => {
    try { await fn(); setError(''); setNotice(okText); }
    catch (e) { setNotice(''); setError(errorText(e)); }
    load();
  };

  if (!order) return error ? <p data-testid="order-error" className="error">{error}</p> : <p>Loading order...</p>;
  const s = order.status;
  return (
    <section className="card">
      <h1>Order <span data-testid="order-number">{order.orderNumber}</span></h1>
      <p>Status: <span className={`badge ${s}`} data-testid="order-status">{s}</span>
        {order.trackingNumber && <> · {order.carrier} <span data-testid="tracking-number">{order.trackingNumber}</span></>}</p>
      {notice && <p data-testid="order-notice" className="ok">{notice}</p>}
      {error && <p data-testid="order-error" className="error">{error}</p>}

      <div className="two-col">
        <div>
          <h2>Items</h2>
          <ul data-testid="order-items">
            {order.items.map(i => <li key={i.productId} data-testid="order-item">{i.quantity} × {i.name} — {money(i.lineTotal)}</li>)}
          </ul>
          <PriceTable q={order} />
          {order.shippingAddress && (
            <p data-testid="order-ship-to"><strong>{t('shipsTo')}:</strong> {addressLine(order.shippingAddress)}</p>
          )}
        </div>
        <div>
          <button className="secondary" data-testid="download-invoice" onClick={downloadInvoice}>{t('downloadInvoice')}</button>
          {s === 'PLACED' && <PayForm onPay={card => act(() => api.post(`/orders/${id}/pay`, card), 'Payment received. Thank you!')}
                                      onPaySaved={body => act(() => api.post(`/orders/${id}/pay-saved`, body), 'Payment received. Thank you!')} />}
          {(s === 'PLACED' || s === 'PAID') && (
            <button className="secondary" data-testid="cancel-order"
                    onClick={() => act(() => api.post(`/orders/${id}/cancel`), 'Order cancelled')}>Cancel order</button>
          )}
          {s === 'DELIVERED' && !ret && <ReturnForm onSubmit={reason => act(() => api.post(`/orders/${id}/return`, { reason }), 'Return requested')} />}
          {ret && <p data-testid="return-status">Return ({ret.reason}): <strong>{ret.status}</strong>
            {ret.refundAmount != null && <> · refunded {money(ret.refundAmount)}</>}</p>}

          <h2>Tracking</h2>
          <ol className="timeline" data-testid="timeline">
            {tracking?.events.map((e, n) => (
              <li key={n} data-testid="timeline-event" data-status={e.status}>
                <strong>{e.status}</strong> <span className="muted">{new Date(e.at).toLocaleString()}</span>
                <div className="muted small">{e.note}</div>
              </li>
            ))}
          </ol>

          {payments.length > 0 && (
            <>
              <h2>Payments</h2>
              <ul data-testid="payments">
                {payments.map(p => (
                  <li key={p.id} data-testid="payment-row" data-status={p.status}>
                    {p.type} {p.status} {money(p.amount)} {p.cardLast4 && `· card •••• ${p.cardLast4}`} {p.failureReason && `· ${p.failureReason}`}
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
      </div>
    </section>
  );
}

function PayForm({ onPay, onPaySaved }) {
  const { t } = useI18n();
  const [card, setCard] = useState({ cardNumber: '', expiryMonth: '12', expiryYear: '2030', cvv: '' });
  const [saveCard, setSaveCard] = useState(false);
  const [saved, setSaved] = useState([]);         // the wallet (REQ-PAY-07)
  const [chosen, setChosen] = useState('');       // id of the saved card in use, '' = a new card
  const set = k => e => setCard({ ...card, [k]: e.target.value });

  useEffect(() => {
    api.get('/payment-methods').then(r => {
      const usable = r.data.filter(m => !m.expired);
      setSaved(usable);
      const def = usable.find(m => m.isDefault) || usable[0];
      if (def) setChosen(String(def.id));
    }).catch(() => {});
  }, []);

  const useSaved = chosen !== '';
  return (
    <form className="pay" data-testid="pay-form" onSubmit={e => {
      e.preventDefault();
      if (useSaved) onPaySaved({ paymentMethodId: Number(chosen), cvv: card.cvv });
      else onPay({ ...card, expiryMonth: Number(card.expiryMonth), expiryYear: Number(card.expiryYear), saveCard });
    }}>
      <h2>Pay</h2>
      {saved.length > 0 && (
        <label>{t('payWithSaved')}
          <select data-testid="saved-card" value={chosen} onChange={e => setChosen(e.target.value)}>
            {saved.map(m => <option key={m.id} value={m.id}>{m.brand} •••• {m.last4} ({String(m.expiryMonth).padStart(2, '0')}/{m.expiryYear})</option>)}
            <option value="">{t('useNewCard')}</option>
          </select>
        </label>
      )}
      {!useSaved && (
        <>
          <label>Card number<input data-testid="card-number" value={card.cardNumber} onChange={set('cardNumber')} placeholder="4242 4242 4242 4242" /></label>
          <div className="row">
            <label>Month<input data-testid="card-month" value={card.expiryMonth} onChange={set('expiryMonth')} className="qty" /></label>
            <label>Year<input data-testid="card-year" value={card.expiryYear} onChange={set('expiryYear')} className="qty" /></label>
          </div>
        </>
      )}
      <label>CVV<input data-testid="card-cvv" value={card.cvv} onChange={set('cvv')} className="qty" autoComplete="off" /></label>
      {!useSaved && (
        <label className="check"><input type="checkbox" checked={saveCard} data-testid="save-card" onChange={e => setSaveCard(e.target.checked)} />{t('saveThisCard')}</label>
      )}
      <button data-testid="pay-button" type="submit">Pay now</button>
      <p className="muted small">Test cards: 4242… approved · 4000 0000 0000 0002 declined</p>
    </form>
  );
}

function ReturnForm({ onSubmit }) {
  const [reason, setReason] = useState('WRONG_ITEM');
  return (
    <div data-testid="return-form">
      <h2>Return this order</h2>
      <select data-testid="return-reason" aria-label="Reason for the return" value={reason} onChange={e => setReason(e.target.value)}>
        <option value="WRONG_ITEM">Wrong item</option>
        <option value="DAMAGED">Damaged</option>
        <option value="NOT_AS_DESCRIBED">Not as described</option>
        <option value="NO_LONGER_NEEDED">No longer needed</option>
      </select>
      <button data-testid="return-submit" onClick={() => onSubmit(reason)}>Request return</button>
    </div>
  );
}
