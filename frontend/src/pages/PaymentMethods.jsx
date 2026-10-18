import React, { useEffect, useState } from 'react';
import api, { errorText } from '../api';
import { useI18n } from '../i18n';

const NUMBER = /^[0-9 ]{13,23}$/;

/** The wallet: up to 5 saved cards, one default. Only brand, last 4 and expiry are ever shown (PaymentMethodService). */
export default function PaymentMethods() {
  const { t } = useI18n();
  const [list, setList] = useState(null);
  const [form, setForm] = useState(null);       // null = closed
  const [errors, setErrors] = useState({});
  const [confirm, setConfirm] = useState(null);
  const [ok, setOk] = useState('');
  const [error, setError] = useState('');

  const load = () => api.get('/payment-methods').then(r => setList(r.data)).catch(e => setError(errorText(e)));
  useEffect(() => { load(); }, []);

  const validate = f => {
    const e = {};
    if (!NUMBER.test(f.cardNumber)) e.cardNumber = t('vCardNumber');
    const m = Number(f.expiryMonth), y = Number(f.expiryYear);
    if (!Number.isInteger(m) || m < 1 || m > 12) e.expiryMonth = t('vMonth');
    if (!Number.isInteger(y) || y < 2000 || y > 2100) e.expiryYear = t('vYear');
    return e;
  };

  const save = async e => {
    e.preventDefault();
    const found = validate(form);
    setErrors(found);
    if (Object.keys(found).length) return;
    try {
      await api.post('/payment-methods', { cardNumber: form.cardNumber, expiryMonth: Number(form.expiryMonth),
        expiryYear: Number(form.expiryYear), makeDefault: form.makeDefault });
      setOk(t('cardSaved')); setError(''); setForm(null); load();
    } catch (err) {
      setOk(''); setError(errorText(err));
    }
  };

  const act = async (fn, message) => {
    try { await fn(); setOk(message); setError(''); setConfirm(null); load(); }
    catch (err) { setOk(''); setError(errorText(err)); }
  };

  return (
    <section className="card" data-testid="wallet-page" aria-labelledby="wallet-title">
      <div className="row between">
        <h1 id="wallet-title">{t('navWallet')}</h1>
        {!form && <button data-testid="card-add" onClick={() => { setErrors({}); setOk(''); setError('');
          setForm({ cardNumber: '', expiryMonth: '12', expiryYear: '2030', makeDefault: false }); }}>{t('addCard')}</button>}
      </div>
      <p className="muted">{t('walletLead')}</p>
      {ok && <p className="success" role="status" data-testid="wallet-ok">{ok}</p>}
      {error && <p className="error" role="alert" data-testid="wallet-error">{error}</p>}

      {form && (
        <form onSubmit={save} noValidate data-testid="card-form" className="card-form">
          <div className="field">
            <label htmlFor="wc-number">{t('cardNumber')}</label>
            <input id="wc-number" data-testid="wc-number" inputMode="numeric" autoComplete="cc-number" value={form.cardNumber}
                   onChange={e => setForm({ ...form, cardNumber: e.target.value })} placeholder="4242 4242 4242 4242" aria-invalid={!!errors.cardNumber} />
            {errors.cardNumber && <span className="field-error" data-testid="wc-number-error">{errors.cardNumber}</span>}
          </div>
          <div className="row">
            <div className="field">
              <label htmlFor="wc-month">{t('month')}</label>
              <input id="wc-month" data-testid="wc-month" value={form.expiryMonth} className="qty" autoComplete="cc-exp-month"
                     onChange={e => setForm({ ...form, expiryMonth: e.target.value })} />
              {errors.expiryMonth && <span className="field-error">{errors.expiryMonth}</span>}
            </div>
            <div className="field">
              <label htmlFor="wc-year">{t('year')}</label>
              <input id="wc-year" data-testid="wc-year" value={form.expiryYear} className="qty" autoComplete="cc-exp-year"
                     onChange={e => setForm({ ...form, expiryYear: e.target.value })} />
              {errors.expiryYear && <span className="field-error">{errors.expiryYear}</span>}
            </div>
          </div>
          <label className="check"><input type="checkbox" checked={form.makeDefault} data-testid="wc-default"
                 onChange={e => setForm({ ...form, makeDefault: e.target.checked })} />{t('makeDefaultCard')}</label>
          <p className="hint">{t('cardNeverKept')}</p>
          <div className="row">
            <button type="submit" data-testid="wc-save">{t('saveCard')}</button>
            <button type="button" className="ghost" onClick={() => setForm(null)}>{t('cancel')}</button>
          </div>
        </form>
      )}

      {list && list.length === 0 && !form && <p data-testid="wallet-empty">{t('walletEmpty')}</p>}
      <ul className="wallet-list" data-testid="wallet-list">
        {(list || []).map(m => (
          <li key={m.id} data-testid="wallet-card" data-last4={m.last4} data-default={m.isDefault}>
            <strong>{m.brand} •••• {m.last4}</strong>
            <span className="muted"> {t('expires')} {String(m.expiryMonth).padStart(2, '0')}/{m.expiryYear}</span>
            {m.expired && <span className="badge warn"> {t('cardExpired')}</span>}
            {m.isDefault && <span className="badge" data-testid="wallet-default"> {t('defaultCard')}</span>}
            <span className="row-actions">
              {!m.isDefault && <button className="link" data-testid="wallet-make-default"
                    onClick={() => act(() => api.post(`/payment-methods/${m.id}/default`), t('defaultCardSet'))}>{t('makeDefaultCard')}</button>}
              {confirm === m.id
                ? <>
                    <button className="danger" data-testid="wallet-confirm-delete" onClick={() => act(() => api.delete(`/payment-methods/${m.id}`), t('cardRemoved'))}>{t('yesDelete')}</button>
                    <button className="link" onClick={() => setConfirm(null)}>{t('cancel')}</button>
                  </>
                : <button className="link" data-testid="wallet-delete" onClick={() => setConfirm(m.id)}>{t('remove')}</button>}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}
