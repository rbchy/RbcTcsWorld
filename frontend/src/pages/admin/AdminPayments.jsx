import React, { useEffect, useState } from 'react';
import api, { errorText, money } from '../../api';
import { useI18n } from '../../i18n';
import { Notice, when } from './common';
import DateInput from '../../DateInput';

const PAGE = 20;
function last30() {
  const to = new Date();
  const from = new Date(to);
  from.setDate(to.getDate() - 29);
  const iso = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  return { from: iso(from), to: iso(to) };
}
const NONE = () => ({ ...last30(), type: '', status: '', q: '' });

/** Every charge and refund: the money trail behind the orders (only the last 4 card digits exist). */
export default function AdminPayments() {
  const { t } = useI18n();
  const [filter, setFilter] = useState(NONE);
  const [applied, setApplied] = useState(filter);
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');

  useEffect(() => {
    api.get('/admin/payments', { params: { from: applied.from || undefined, to: applied.to || undefined,
      type: applied.type || undefined, status: applied.status || undefined, q: applied.q.trim() || undefined, page, size: PAGE } })
      .then(r => { setData(r.data); setError(''); }).catch(e => setError(errorText(e)));
  }, [applied, page]);

  const set = k => e => setFilter({ ...filter, [k]: e.target.value });
  const search = e => {
    e.preventDefault();
    const found = filter.from && filter.to && filter.from > filter.to ? { to: 'vEndBeforeStart' } : {};
    setErrors(found);
    if (Object.keys(found).length) return;
    setPage(0); setApplied(filter);
  };
  const pages = data ? Math.max(1, Math.ceil(data.total / PAGE)) : 1;

  return (
    <section data-testid="admin-payments">
      <header className="page-head"><h1>{t('paymentsTitle')}</h1></header>
      <p className="muted small">{t('paymentsHint')}</p>
      <Notice error={error} />
      <form className="card filters" role="search" aria-label={t('paymentsTitle')} onSubmit={search} noValidate data-testid="payment-search">
        <div className="field">
          <label htmlFor="py-from">{t('fromDate')}</label>
          <DateInput id="py-from" value={filter.from} onChange={set('from')} data-testid="py-from" />
        </div>
        <div className="field">
          <label htmlFor="py-to">{t('toDate')}</label>
          <DateInput id="py-to" value={filter.to} onChange={set('to')} data-testid="py-to" aria-invalid={!!errors.to}
                 aria-describedby={errors.to ? 'py-to-error' : undefined} />
          {errors.to && <span className="field-error" id="py-to-error" data-testid="py-to-error">{t(errors.to)}</span>}
        </div>
        <label className="field">{t('type')}
          <select value={filter.type} onChange={set('type')} data-testid="py-type">
            <option value="">{t('allTypes')}</option>
            <option value="CHARGE">{t('charge')}</option>
            <option value="REFUND">{t('refund')}</option>
          </select>
        </label>
        <label className="field">{t('status')}
          <select value={filter.status} onChange={set('status')} data-testid="py-status">
            <option value="">{t('allStatuses')}</option>
            <option value="SUCCEEDED">{t('succeeded')}</option>
            <option value="FAILED">{t('failed')}</option>
          </select>
        </label>
        <label className="field">{t('order')}
          <input type="search" value={filter.q} onChange={set('q')} data-testid="py-q" />
        </label>
        <div className="filter-actions">
          <button type="submit" data-testid="py-search">{t('search')}</button>
          <button type="button" className="ghost" data-testid="py-reset"
                  onClick={() => { const n = NONE(); setFilter(n); setErrors({}); setPage(0); setApplied(n); }}>{t('reset')}</button>
        </div>
      </form>
      {!data ? <p>{t('loading')}</p> : (
        <>
          <ul className="stats compact" data-testid="payment-sums">
            <li><span><strong data-testid="py-charges">{money(data.succeededCharges)}</strong>{t('chargedTotal')}</span></li>
            <li><span><strong data-testid="py-refunds">{money(data.succeededRefunds)}</strong>{t('refundedTotal')}</span></li>
            <li><span><strong>{money(data.succeededCharges - data.succeededRefunds)}</strong>{t('netTotal')}</span></li>
          </ul>
          <div className="table-wrap card" tabIndex={0} role="region" aria-label={t('paymentsTitle')}>
            <table data-testid="payment-table">
              <thead><tr><th>{t('date')}</th><th>{t('order')}</th><th>{t('type')}</th><th>{t('status')}</th>
                <th className="num">{t('amount')}</th><th>{t('card')}</th><th>{t('provider')}</th></tr></thead>
              <tbody>
                {data.items.length === 0 && <tr><td colSpan="7" className="muted">{t('noRows')}</td></tr>}
                {data.items.map(p => (
                  <tr key={p.id} data-testid="payment-row">
                    <td className="small">{when(p.createdAt)}</td>
                    <td>{p.orderNumber}</td>
                    <td>{p.type === 'CHARGE' ? t('charge') : t('refund')}</td>
                    <td><span className={`badge ${p.status === 'FAILED' ? 'state-disabled' : 'state-active'}`}>
                      {p.status === 'FAILED' ? t('failed') : t('succeeded')}</span>
                      {p.failureReason && <div className="small error-text">{p.failureReason}</div>}</td>
                    <td className="num">{p.type === 'REFUND' ? `−${money(p.amount)}` : money(p.amount)}</td>
                    <td className="small">{p.cardLast4 ? `•••• ${p.cardLast4}` : '-'}</td>
                    <td className="small">{p.provider}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {pages > 1 && (
            <nav className="row pager" aria-label={t('pages')}>
              <button type="button" className="ghost" disabled={page === 0} onClick={() => setPage(page - 1)}>{t('previous')}</button>
              <span className="small">{t('pageOf', { n: page + 1, total: pages })}</span>
              <button type="button" className="ghost" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>{t('next')}</button>
            </nav>
          )}
        </>
      )}
    </section>
  );
}
