import React, { useEffect, useState } from 'react';
import api, { errorText } from '../api';
import { useI18n } from '../i18n';
import { useSession } from '../session';
import { Field } from './admin/common';

const TOPICS = ['ORDER', 'RETURN', 'PRODUCT', 'PAYMENT', 'OTHER'];
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const EMPTY = { name: '', email: '', topic: 'ORDER', orderNumber: '', message: '', website: '' };

/** Same rules as the backend ContactRequest/ContactService (REQ-CS-01). The e-mail comes from the account when logged in. */
export function validateContact(f, loggedIn) {
  const e = {};
  if (!f.name.trim()) e.name = 'vRequired';
  else if (f.name.trim().length > 100) e.name = 'vTooLong';
  if (!loggedIn) {
    if (!f.email.trim()) e.email = 'vEmailRequired';
    else if (!EMAIL.test(f.email.trim()) || f.email.trim().length > 254) e.email = 'vEmailInvalid';
  }
  if (f.orderNumber.trim() && !/^[A-Za-z0-9-]{1,40}$/.test(f.orderNumber.trim())) e.orderNumber = 'vOrderNumber';
  const m = f.message.trim();
  if (m.length < 10) e.message = 'vMessageShort';
  else if (m.length > 4000) e.message = 'vTooLong';
  return e;
}

/**
 * Contact form (ADR-0018): anybody may write; a logged-in customer writes with the account's own address.
 * The "website" field is a honeypot: hidden from people (and screen readers), filled in only by bots.
 */
export default function Contact() {
  const { t } = useI18n();
  const session = useSession();
  const [info, setInfo] = useState({ replyDays: 2 });
  const [shopEmail, setShopEmail] = useState(null);
  const [form, setForm] = useState(EMPTY);
  const [errors, setErrors] = useState({});
  const [sent, setSent] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.get('/contact').then(r => setInfo(r.data)).catch(() => {});
    api.get('/store/legal').then(r => setShopEmail(r.data.contactEmail)).catch(() => {});
  }, []);

  const set = k => e => {
    const next = { ...form, [k]: e.target.value };
    setForm(next);
    if (errors[k]) setErrors(validateContact(next, !!session));
  };

  const submit = async e => {
    e.preventDefault();
    const found = validateContact(form, !!session);
    setErrors(found);
    if (Object.keys(found).length) return;
    setBusy(true);
    try {
      const r = await api.post('/contact', { name: form.name.trim(), email: session ? null : form.email.trim(), topic: form.topic,
        orderNumber: form.orderNumber.trim() || null, message: form.message.trim(), website: form.website || null });
      setSent(r.data); setError(''); setForm(EMPTY);
    } catch (err) { setError(errorText(err)); }
    finally { setBusy(false); }
  };

  if (sent) {
    return (
      <section className="card narrow" data-testid="contact-sent">
        <h1>{t('contactSentTitle')}</h1>
        <p role="status">{t('contactSent', { ticket: sent.ticket, days: sent.replyDays })}</p>
        <p className="ticket" data-testid="contact-ticket">{sent.ticket}</p>
        <p className="row">
          <a href="#/" className="button">{t('navCatalog')}</a>
          <button type="button" className="ghost" onClick={() => setSent(null)} data-testid="contact-again">{t('contactAgain')}</button>
        </p>
      </section>
    );
  }

  return (
    <section className="card narrow" data-testid="contact-page">
      <h1>{t('contactTitle')}</h1>
      <p>{t('contactLead', { days: info.replyDays })}</p>
      {shopEmail && <p className="small">{t('contactOrEmail')} <a href={`mailto:${shopEmail}`} data-testid="contact-shop-email">{shopEmail}</a></p>}
      {error && <p className="error" role="alert" data-testid="contact-error">{error}</p>}
      <form onSubmit={submit} noValidate className="form-grid" data-testid="contact-form">
        <Field id="ct-name" label={t('contactName')} error={errors.name}>
          <input value={form.name} onChange={set('name')} maxLength={100} autoComplete="name" data-testid="ct-name" />
        </Field>
        {session ? (
          <div className="field">
            <span className="label">{t('email')}</span>
            <p className="muted" data-testid="ct-account-email">{session.email} · {t('contactFromAccount')}</p>
          </div>
        ) : (
          <Field id="ct-email" label={t('email')} error={errors.email}>
            <input type="email" value={form.email} onChange={set('email')} maxLength={254} autoComplete="email" data-testid="ct-email" />
          </Field>
        )}
        <Field id="ct-topic" label={t('contactTopic')}>
          <select value={form.topic} onChange={set('topic')} data-testid="ct-topic">
            {TOPICS.map(x => <option key={x} value={x}>{t(`topic${x}`)}</option>)}
          </select>
        </Field>
        <Field id="ct-order" label={t('contactOrder')} error={errors.orderNumber} hint={t('optional')}>
          <input value={form.orderNumber} onChange={set('orderNumber')} maxLength={40} data-testid="ct-order" />
        </Field>
        <div className="full">
          <Field id="ct-message" label={t('contactMessage')} error={errors.message} hint={`${form.message.trim().length} / 4000`}>
            <textarea rows={7} value={form.message} onChange={set('message')} maxLength={4000} data-testid="ct-message" />
          </Field>
        </div>
        {/* honeypot: not for people */}
        <div className="hp" aria-hidden="true">
          <label htmlFor="ct-website">Website</label>
          <input id="ct-website" tabIndex={-1} autoComplete="off" value={form.website} onChange={set('website')} />
        </div>
        <div className="row form-actions full">
          <button type="submit" disabled={busy} data-testid="ct-send">{busy ? t('sending') : t('contactSend')}</button>
        </div>
      </form>
      <p className="small muted">{t('contactPrivacy')} <a href="#/privacy">{t('navPrivacy')}</a></p>
    </section>
  );
}
