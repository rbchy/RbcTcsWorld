import React, { useEffect, useState } from 'react';
import api, { errorText } from '../api';
import { useI18n } from '../i18n';
import { Icon } from '../icons';
import AddressFields, { EMPTY_ADDRESS, addressBody, nullsToEmpty, validateAddress } from '../AddressFields';

const LABEL = /^[\p{L}\p{N} .'-]{0,30}$/u;

/** The address book: up to 10 delivery addresses, one default (used first at checkout). Same rules as AddressService. */
export default function Addresses() {
  const { t } = useI18n();
  const [list, setList] = useState(null);
  const [form, setForm] = useState(null);           // null = closed; {id?, label, address, makeDefault}
  const [errors, setErrors] = useState({});
  const [confirm, setConfirm] = useState(null);
  const [ok, setOk] = useState('');
  const [error, setError] = useState('');

  const load = () => api.get('/addresses').then(r => setList(r.data)).catch(e => setError(errorText(e)));
  useEffect(() => { load(); }, []);

  const open = a => {
    setErrors({}); setOk(''); setError(''); setConfirm(null);
    setForm(a ? { id: a.id, label: a.label || '', address: nullsToEmpty(a.address), makeDefault: a.isDefault }
              : { label: '', address: EMPTY_ADDRESS, makeDefault: false });
  };
  const validate = f => {
    const found = Object.fromEntries(Object.entries(validateAddress(f.address)).map(([k, v]) => [k, t(v)]));
    if (!LABEL.test(f.label)) found.label = t('vLabel');
    return found;
  };
  const change = (field, value) => {
    const next = { ...form, address: { ...form.address, [field]: value } };
    setForm(next);
    if (errors[field]) setErrors(validate(next));
  };

  const save = async e => {
    e.preventDefault();
    const found = validate(form);
    setErrors(found);
    if (Object.keys(found).length) return;
    const body = { label: form.label.trim() || null, address: addressBody(form.address), makeDefault: form.makeDefault };
    try {
      if (form.id) await api.put(`/addresses/${form.id}`, body);
      else await api.post('/addresses', body);
      setOk(form.id ? t('addressUpdated') : t('addressSaved')); setError(''); setForm(null); load();
    } catch (err) {
      const server = err?.response?.data?.fieldErrors || {};
      setErrors(Object.fromEntries(Object.entries(server).map(([f, m]) => [f.replace(/^address\./, ''), m])));
      setOk(''); setError(errorText(err));
    }
  };

  const act = async (fn, message) => {
    try { await fn(); setOk(message); setError(''); setConfirm(null); load(); }
    catch (err) { setOk(''); setError(errorText(err)); }
  };

  return (
    <section data-testid="addresses-page">
      <header className="page-head">
        <h1>{t('addressesTitle')}</h1>
        {list && list.length < 10 && (
          <button className="with-icon" onClick={() => open(null)} data-testid="address-add"><Icon name="plus" />{t('addAddress')}</button>
        )}
      </header>
      <p className="muted small">{t('addressesLead')}</p>
      {ok && <p className="ok" role="status" data-testid="address-notice">{ok}</p>}
      {error && <p className="error" role="alert" data-testid="address-error">{error}</p>}

      {form && (
        <form className="card form-grid address-form" onSubmit={save} noValidate data-testid="address-book-form" aria-labelledby="ab-title">
          <h2 id="ab-title">{form.id ? t('editAddress') : t('addAddress')}</h2>
          <div className="field">
            <label htmlFor="ab-label">{t('addressLabel')}</label>
            <input id="ab-label" value={form.label} maxLength={30} placeholder={t('addressLabelHint')} data-testid="ab-label"
                   onChange={e => setForm({ ...form, label: e.target.value })} aria-invalid={!!errors.label}
                   aria-describedby={errors.label ? 'ab-label-error' : undefined} />
            {errors.label && <span className="field-error" id="ab-label-error" data-testid="ab-label-error">{errors.label}</span>}
          </div>
          <AddressFields prefix="ab" value={form.address} onChange={change} errors={errors} t={t} />
          <label className="check form-actions">
            <input type="checkbox" checked={form.makeDefault} onChange={e => setForm({ ...form, makeDefault: e.target.checked })}
                   data-testid="ab-default" />{t('useAsDefault')}
          </label>
          <div className="row form-actions">
            <button type="submit" data-testid="ab-save">{t('saveAddressBtn')}</button>
            <button type="button" className="ghost" onClick={() => setForm(null)} data-testid="ab-cancel">{t('cancel')}</button>
          </div>
        </form>
      )}

      {list && list.length === 0 && !form && (
        <p className="card muted" data-testid="addresses-empty">{t('noAddresses')}</p>
      )}
      <ul className="address-cards" data-testid="address-list">
        {(list || []).map(a => (
          <li key={a.id} className={`card address-card${a.isDefault ? ' is-default' : ''}`} data-testid="address-card" data-default={a.isDefault}>
            <div className="row between">
              <h2><Icon name="pin" />{a.label || a.address.fullName}</h2>
              {a.isDefault && <span className="badge state-active" data-testid="address-default">{t('defaultBadge')}</span>}
            </div>
            <address>
              {a.address.fullName}<br />{a.address.line1}{a.address.line2 && <>, {a.address.line2}</>}<br />
              {a.address.city}, {a.address.state} {a.address.zip}{a.address.phone && <><br />{a.address.phone}</>}
            </address>
            <div className="row">
              <button className="ghost" onClick={() => open(a)} data-testid="address-edit">{t('edit')}</button>
              {!a.isDefault && <button className="ghost" data-testid="address-make-default"
                                       onClick={() => act(() => api.post(`/addresses/${a.id}/default`), t('defaultChanged'))}>{t('makeDefault')}</button>}
              <button className="ghost danger-text" onClick={() => setConfirm(a.id)} data-testid="address-delete">{t('delete')}</button>
            </div>
            {confirm === a.id && (
              <div className="confirm" data-testid="address-confirm">
                <p>{t('confirmDeleteAddress')}</p>
                <button className="danger-btn" data-testid="address-delete-yes"
                        onClick={() => act(() => api.delete(`/addresses/${a.id}`), t('addressDeleted'))}>{t('yesDelete')}</button>
                <button className="ghost" onClick={() => setConfirm(null)} data-testid="address-delete-no">{t('cancel')}</button>
              </div>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}
