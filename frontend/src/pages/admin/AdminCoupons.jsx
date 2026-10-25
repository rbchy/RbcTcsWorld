import React, { useEffect, useRef, useState } from 'react';
import api, { errorText, money } from '../../api';
import { useI18n } from '../../i18n';
import { Field, Notice, when } from './common';
import DateInput from '../../DateInput';

const EMPTY = { code: '', type: 'PERCENT', value: '', minOrderAmount: '0', maxUses: '', validFrom: '', validUntil: '' };
export const COUPON_STATES = ['ACTIVE', 'SCHEDULED', 'EXPIRED', 'USED_UP', 'DISABLED'];

/**
 * Mirrors Create/UpdateCouponRequest + CouponService: code pattern, value > 0 with 2 decimals, PERCENT at most
 * 100, maxUses >= 1 (and not below the times already used when editing), end not before start.
 */
export function validateCoupon(f, usedCount = 0) {
  const e = {};
  if (!f.code.trim()) e.code = 'vRequired';
  else if (!/^[A-Za-z0-9_-]+$/.test(f.code.trim())) e.code = 'vCode';
  if (f.value === '' || Number.isNaN(Number(f.value))) e.value = 'vRequired';
  else if (Number(f.value) <= 0) e.value = 'vPositive';
  else if (!/^\d+(\.\d{1,2})?$/.test(String(f.value).trim())) e.value = 'vMoney';
  else if (f.type === 'PERCENT' && Number(f.value) > 100) e.value = 'vPercent';
  if (f.minOrderAmount !== '' && !/^\d+(\.\d{1,2})?$/.test(String(f.minOrderAmount).trim())) e.minOrderAmount = 'vMoney';
  if (f.maxUses !== '' && (!/^\d+$/.test(String(f.maxUses).trim()) || Number(f.maxUses) < 1)) e.maxUses = 'vPositive';
  else if (f.maxUses !== '' && Number(f.maxUses) < usedCount) e.maxUses = 'vBelowUsed';
  if (f.validFrom && f.validUntil && f.validUntil < f.validFrom) e.validUntil = 'vEndBeforeStart';
  return e;
}

const toInput = v => (v ? String(v).slice(0, 16) : '');               // 2026-10-17T09:30:00 -> datetime-local
const fromInput = v => (v ? (v.length === 16 ? `${v}:00` : v) : null);

/** Coupons: create, edit, switch off / on, and find them by code and state. */
export default function AdminCoupons() {
  const { t } = useI18n();
  const [rows, setRows] = useState([]);
  const [form, setForm] = useState({ ...EMPTY });
  const [editing, setEditing] = useState(null);                       // the coupon being edited, or null
  const [errors, setErrors] = useState({});
  const [search, setSearch] = useState({ q: '', status: '' });
  const [applied, setApplied] = useState(search);
  const [ok, setOk] = useState('');
  const [error, setError] = useState('');

  // DEF-029: a reload after Save can still be on its way when the admin searches. Only the newest request may fill the
  // table; an older, unfiltered answer that arrives late is dropped instead of replacing the search results.
  const latest = useRef(0);
  const load = (f = applied) => {
    const ticket = ++latest.current;
    return api.get('/admin/coupons', { params: { q: f.q.trim() || undefined, status: f.status || undefined } })
      .then(r => { if (ticket === latest.current) setRows(r.data); })
      .catch(e => { if (ticket === latest.current) setError(errorText(e)); });
  };
  useEffect(() => { load(applied); }, [applied]);

  const used = editing ? editing.usedCount : 0;
  const set = k => e => {
    const next = { ...form, [k]: e.target.value };
    setForm(next);
    if (errors[k] || (k === 'type' && errors.value) || (k === 'validFrom' && errors.validUntil)) setErrors(validateCoupon(next, used));
  };

  const startEdit = c => {
    setEditing(c);
    setErrors({}); setOk(''); setError('');
    setForm({ code: c.code, type: c.type, value: String(c.discountValue), minOrderAmount: String(c.minOrderAmount),
      maxUses: c.maxUses == null ? '' : String(c.maxUses), validFrom: toInput(c.validFrom), validUntil: toInput(c.validUntil) });
    document.getElementById('cf-code')?.focus();
  };
  const stopEdit = () => { setEditing(null); setForm({ ...EMPTY }); setErrors({}); };

  const save = async e => {
    e.preventDefault();
    const found = validateCoupon(form, used);
    setErrors(found);
    if (Object.keys(found).length) return;
    const terms = {
      type: form.type, value: Number(form.value),
      minOrderAmount: form.minOrderAmount === '' ? null : Number(form.minOrderAmount),
      maxUses: form.maxUses === '' ? null : Number(form.maxUses),
      validFrom: fromInput(form.validFrom), validUntil: fromInput(form.validUntil),
    };
    try {
      if (editing) {
        await api.put(`/admin/coupons/${editing.id}`, terms);
        setOk(`${t('couponSaved')}: ${editing.code}`);
        stopEdit();
      } else {
        await api.post('/admin/coupons', { code: form.code.trim(), ...terms });
        setOk(t('couponCreated'));
        setForm({ ...EMPTY });
      }
      setError(''); load();
    } catch (err) { setOk(''); setError(errorText(err)); }
  };

  const toggle = async c => {
    try {
      await api.post(`/admin/coupons/${c.id}/${c.active ? 'disable' : 'enable'}`);
      setOk(`${c.active ? t('couponDisabled') : t('couponEnabled')}: ${c.code}`); setError(''); load();
    } catch (err) { setOk(''); setError(errorText(err)); }
  };

  const stateOf = c => {
    const now = new Date();
    if (!c.active) return 'DISABLED';
    if (c.validUntil && new Date(c.validUntil) < now) return 'EXPIRED';
    if (c.maxUses != null && c.usedCount >= c.maxUses) return 'USED_UP';
    if (c.validFrom && new Date(c.validFrom) > now) return 'SCHEDULED';
    return 'ACTIVE';
  };

  return (
    <section data-testid="admin-coupons">
      <header className="page-head"><h1>{t('couponsTitle')}</h1></header>
      <Notice ok={ok} error={error} />

      <form className="card form-grid" onSubmit={save} noValidate data-testid="coupon-form" aria-labelledby="coupon-form-title"
            data-mode={editing ? 'edit' : 'create'}>
        <h2 id="coupon-form-title">{editing ? `${t('editCoupon')}: ${editing.code}` : t('createCoupon')}</h2>
        <Field id="cf-code" label={t('code')} error={errors.code} hint={editing ? t('codeFixed') : undefined}>
          <input value={form.code} onChange={set('code')} maxLength={40} data-testid="cf-code" readOnly={!!editing} />
        </Field>
        <Field id="cf-type" label={t('type')}>
          <select value={form.type} onChange={set('type')} data-testid="cf-type">
            <option value="PERCENT">%</option>
            <option value="FIXED">$</option>
          </select>
        </Field>
        <Field id="cf-value" label={t('value')} error={errors.value}>
          <input value={form.value} onChange={set('value')} inputMode="decimal" data-testid="cf-value" />
        </Field>
        <Field id="cf-min" label={t('minOrder')} error={errors.minOrderAmount}>
          <input value={form.minOrderAmount} onChange={set('minOrderAmount')} inputMode="decimal" data-testid="cf-min" />
        </Field>
        <Field id="cf-max" label={t('maxUses')} error={errors.maxUses}>
          <input value={form.maxUses} onChange={set('maxUses')} inputMode="numeric" data-testid="cf-max" />
        </Field>
        <Field id="cf-from" label={t('validFrom')} error={errors.validFrom}>
          <DateInput type="datetime-local" defaultTime="00:00" value={form.validFrom} onChange={set('validFrom')} data-testid="cf-from" />
        </Field>
        <Field id="cf-until" label={t('validUntil')} error={errors.validUntil}>
          <DateInput type="datetime-local" defaultTime="23:59" value={form.validUntil} onChange={set('validUntil')} data-testid="cf-until" />
        </Field>
        <div className="row form-actions">
          <button type="submit" data-testid="cf-save">{editing ? t('saveChanges') : t('createCoupon')}</button>
          {editing && <button type="button" className="ghost" onClick={stopEdit} data-testid="cf-cancel">{t('cancel')}</button>}
        </div>
      </form>

      <form className="card filters" role="search" aria-label={t('searchCoupons')} data-testid="coupon-search"
            onSubmit={e => { e.preventDefault(); setApplied(search); }}>
        <label className="field">{t('code')}
          <input type="search" value={search.q} onChange={e => setSearch({ ...search, q: e.target.value })} data-testid="cs-q" />
        </label>
        <label className="field">{t('status')}
          <select value={search.status} onChange={e => setSearch({ ...search, status: e.target.value })} data-testid="cs-status">
            <option value="">{t('allStatuses')}</option>
            {COUPON_STATES.map(s => <option key={s} value={s}>{t(`cs${s}`)}</option>)}
          </select>
        </label>
        <div className="filter-actions">
          <button type="submit" data-testid="cs-search">{t('search')}</button>
          <button type="button" className="ghost" data-testid="cs-reset"
                  onClick={() => { const none = { q: '', status: '' }; setSearch(none); setApplied(none); }}>{t('reset')}</button>
        </div>
      </form>

      <div className="table-wrap card" tabIndex={0} role="region" aria-label={t('couponsTitle')}>
        <table data-testid="coupon-table">
          <thead><tr><th>{t('code')}</th><th>{t('type')}</th><th className="num">{t('value')}</th><th className="num">{t('minOrder')}</th>
            <th className="num">{t('used')}</th><th>{t('validity')}</th><th>{t('status')}</th><th>{t('actions')}</th></tr></thead>
          <tbody>
            {rows.length === 0 && <tr><td colSpan="8" className="muted">{t('noRows')}</td></tr>}
            {rows.map(c => (
              <tr key={c.id} data-testid="coupon-row" data-code={c.code}>
                <td><strong>{c.code}</strong></td>
                <td>{c.type === 'PERCENT' ? '%' : '$'}</td>
                <td className="num">{c.type === 'PERCENT' ? `${Number(c.discountValue)}%` : money(c.discountValue)}</td>
                <td className="num">{money(c.minOrderAmount)}</td>
                <td className="num">{c.usedCount}{c.maxUses ? ` / ${c.maxUses}` : ''}</td>
                <td className="small">{c.validFrom ? when(c.validFrom) : '…'} – {c.validUntil ? when(c.validUntil) : '…'}</td>
                <td><span className={`badge state-${stateOf(c).toLowerCase()}`} data-testid="coupon-state">{t(`cs${stateOf(c)}`)}</span></td>
                <td className="actions">
                  <button type="button" className="ghost" onClick={() => startEdit(c)} data-testid="coupon-edit"
                          aria-label={`${t('edit')} ${c.code}`}>{t('edit')}</button>
                  <button type="button" className={c.active ? 'ghost danger-text' : 'ghost'} onClick={() => toggle(c)}
                          data-testid="coupon-toggle" aria-label={`${c.active ? t('disable') : t('enable')} ${c.code}`}>
                    {c.active ? t('disable') : t('enable')}</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
