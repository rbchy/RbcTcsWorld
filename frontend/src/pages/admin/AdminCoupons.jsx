import React, { useEffect, useState } from 'react';
import api, { errorText, money } from '../../api';
import { useI18n } from '../../i18n';
import { Field, Notice, when } from './common';

const EMPTY = { code: '', type: 'PERCENT', value: '', minOrderAmount: '0', maxUses: '' };

/** Mirrors CreateCouponRequest + CouponService: code pattern, value > 0, PERCENT at most 100, maxUses >= 1. */
export function validateCoupon(f) {
  const e = {};
  if (!f.code.trim()) e.code = 'vRequired';
  else if (!/^[A-Za-z0-9_-]+$/.test(f.code.trim())) e.code = 'vCode';
  if (f.value === '' || Number.isNaN(Number(f.value))) e.value = 'vRequired';
  else if (Number(f.value) <= 0) e.value = 'vPositive';
  else if (!/^\d+(\.\d{1,2})?$/.test(String(f.value).trim())) e.value = 'vMoney';
  else if (f.type === 'PERCENT' && Number(f.value) > 100) e.value = 'vPercent';
  if (f.minOrderAmount !== '' && !/^\d+(\.\d{1,2})?$/.test(String(f.minOrderAmount).trim())) e.minOrderAmount = 'vMoney';
  if (f.maxUses !== '' && (!/^\d+$/.test(String(f.maxUses).trim()) || Number(f.maxUses) < 1)) e.maxUses = 'vPositive';
  return e;
}

export default function AdminCoupons() {
  const { t } = useI18n();
  const [rows, setRows] = useState([]);
  const [form, setForm] = useState({ ...EMPTY });
  const [errors, setErrors] = useState({});
  const [ok, setOk] = useState('');
  const [error, setError] = useState('');

  const load = () => api.get('/admin/coupons').then(r => setRows(r.data)).catch(e => setError(errorText(e)));
  useEffect(() => { load(); }, []);

  const set = k => e => {
    const next = { ...form, [k]: e.target.value };
    setForm(next);
    if (errors[k] || (k === 'type' && errors.value)) setErrors(validateCoupon(next));
  };

  const create = async e => {
    e.preventDefault();
    const found = validateCoupon(form);
    setErrors(found);
    if (Object.keys(found).length) return;
    try {
      await api.post('/admin/coupons', {
        code: form.code.trim(), type: form.type, value: Number(form.value),
        minOrderAmount: form.minOrderAmount === '' ? null : Number(form.minOrderAmount),
        maxUses: form.maxUses === '' ? null : Number(form.maxUses),
      });
      setOk(t('couponCreated')); setError(''); setForm({ ...EMPTY }); load();
    } catch (err) { setOk(''); setError(errorText(err)); }
  };

  return (
    <section data-testid="admin-coupons">
      <header className="page-head"><h1>{t('couponsTitle')}</h1></header>
      <Notice ok={ok} error={error} />
      <form className="card form-grid" onSubmit={create} noValidate data-testid="coupon-form" aria-labelledby="coupon-form-title">
        <h2 id="coupon-form-title">{t('createCoupon')}</h2>
        <Field id="cf-code" label={t('code')} error={errors.code}>
          <input value={form.code} onChange={set('code')} maxLength={40} data-testid="cf-code" />
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
        <div className="row form-actions"><button type="submit" data-testid="cf-save">{t('createCoupon')}</button></div>
      </form>
      <div className="table-wrap card">
        <table data-testid="coupon-table">
          <thead><tr><th>{t('code')}</th><th>{t('type')}</th><th className="num">{t('value')}</th><th className="num">{t('minOrder')}</th>
            <th className="num">{t('used')}</th><th>{t('status')}</th></tr></thead>
          <tbody>
            {rows.length === 0 && <tr><td colSpan="6" className="muted">{t('noRows')}</td></tr>}
            {rows.map(c => (
              <tr key={c.id} data-testid="coupon-row" data-code={c.code}>
                <td><strong>{c.code}</strong></td>
                <td>{c.type === 'PERCENT' ? '%' : '$'}</td>
                <td className="num">{c.type === 'PERCENT' ? `${Number(c.discountValue)}%` : money(c.discountValue)}</td>
                <td className="num">{money(c.minOrderAmount)}</td>
                <td className="num">{c.usedCount}{c.maxUses ? ` / ${c.maxUses}` : ''}</td>
                <td className="small">{c.active ? 'ACTIVE' : 'DISABLED'}{c.validUntil ? ` · ${when(c.validUntil)}` : ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
