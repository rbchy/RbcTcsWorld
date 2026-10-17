import React, { useEffect, useState } from 'react';
import api, { errorText, money } from '../../api';
import { useI18n } from '../../i18n';
import { Notice, when } from './common';
import { addressLine } from '../../usStates';

const PAGE = 20;
const SORTS = ['newest', 'email', 'orders', 'spent', 'lastOrder'];
const NONE = { q: '', status: '', sort: 'newest' };

/** Customer accounts: search, look at one customer (orders, last address), disable or enable the account. */
export default function AdminCustomers() {
  const { t } = useI18n();
  const [filter, setFilter] = useState(NONE);
  const [applied, setApplied] = useState(NONE);
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [detail, setDetail] = useState(null);
  const [confirming, setConfirming] = useState(false);
  const [ok, setOk] = useState('');
  const [error, setError] = useState('');

  const load = () => api.get('/admin/customers', { params: {
    q: applied.q.trim() || undefined, status: applied.status || undefined, sort: applied.sort, page, size: PAGE } })
    .then(r => { setData(r.data); setError(''); }).catch(e => setError(errorText(e)));
  useEffect(() => { load(); }, [applied, page]);

  const open = id => api.get(`/admin/customers/${id}`)
    .then(r => { setDetail(r.data); setConfirming(false); setOk(''); })
    .catch(e => setError(errorText(e)));

  const setActive = async active => {
    const c = detail.customer;
    try {
      await api.post(`/admin/customers/${c.id}/${active ? 'enable' : 'disable'}`);
      setOk(`${active ? t('customerEnabled') : t('customerDisabled')}: ${c.email}`);
      setError(''); setConfirming(false);
      open(c.id); load();
    } catch (err) { setOk(''); setError(errorText(err)); }
  };

  const set = k => e => setFilter({ ...filter, [k]: e.target.value });
  const pages = data ? Math.max(1, Math.ceil(data.total / PAGE)) : 1;

  return (
    <section data-testid="admin-customers">
      <header className="page-head"><h1>{t('customersTitle')}</h1></header>
      <p className="muted small">{t('customersHint')}</p>
      <Notice ok={ok} error={error} />

      <form className="card filters" role="search" aria-label={t('searchCustomers')} data-testid="customer-search"
            onSubmit={e => { e.preventDefault(); setPage(0); setApplied(filter); }}>
        <label className="field">{t('email')}
          <input type="search" value={filter.q} onChange={set('q')} data-testid="cu-q" />
        </label>
        <label className="field">{t('status')}
          <select value={filter.status} onChange={set('status')} data-testid="cu-status">
            <option value="">{t('allStatuses')}</option>
            <option value="ACTIVE">{t('accountActive')}</option>
            <option value="DISABLED">{t('accountDisabled')}</option>
          </select>
        </label>
        <label className="field">{t('sortBy')}
          <select value={filter.sort} onChange={set('sort')} data-testid="cu-sort">
            {SORTS.map(s => <option key={s} value={s}>{t(`cuSort_${s}`)}</option>)}
          </select>
        </label>
        <div className="filter-actions">
          <button type="submit" data-testid="cu-search">{t('search')}</button>
          <button type="button" className="ghost" data-testid="cu-reset"
                  onClick={() => { setFilter(NONE); setPage(0); setApplied(NONE); }}>{t('reset')}</button>
        </div>
      </form>

      {!data ? <p>{t('loading')}</p> : (
        <>
          <p className="small muted" data-testid="customer-count">{t('customersFound', { n: data.total })}</p>
          <div className="table-wrap card" tabIndex={0} role="region" aria-label={t('customersTitle')}>
            <table data-testid="customer-table">
              <thead><tr><th>{t('email')}</th><th>{t('joined')}</th><th className="num">{t('statOrders')}</th>
                <th className="num">{t('spent')}</th><th>{t('lastOrder')}</th><th>{t('status')}</th><th>{t('actions')}</th></tr></thead>
              <tbody>
                {data.items.length === 0 && <tr><td colSpan="7" className="muted">{t('noRows')}</td></tr>}
                {data.items.map(c => (
                  <tr key={c.id} data-testid="customer-row" data-email={c.email}>
                    <td>{c.email}</td>
                    <td className="small">{when(c.createdAt)}</td>
                    <td className="num">{c.orders}</td>
                    <td className="num">{money(c.spent)}</td>
                    <td className="small">{c.lastOrderAt ? when(c.lastOrderAt) : '-'}</td>
                    <td><span className={`badge ${c.active ? 'state-active' : 'state-disabled'}`} data-testid="customer-state">
                      {c.active ? t('accountActive') : t('accountDisabled')}</span></td>
                    <td className="actions">
                      <button type="button" className="ghost" onClick={() => open(c.id)} data-testid="customer-view"
                              aria-label={`${t('view')} ${c.email}`}>{t('view')}</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {pages > 1 && (
            <nav className="row pager" aria-label={t('pages')}>
              <button type="button" className="ghost" disabled={page === 0} onClick={() => setPage(page - 1)} data-testid="cu-prev">{t('previous')}</button>
              <span className="small">{t('pageOf', { n: page + 1, total: pages })}</span>
              <button type="button" className="ghost" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)} data-testid="cu-next">{t('next')}</button>
            </nav>
          )}
        </>
      )}

      {detail && (
        <section className="card detail-panel" aria-labelledby="customer-detail-title" data-testid="customer-detail">
          <header className="page-head">
            <div>
              <h2 id="customer-detail-title">{detail.customer.email}</h2>
              <p className="small muted">{t('joined')}: {when(detail.customer.createdAt)} · {t('spent')}: {money(detail.customer.spent)}</p>
            </div>
            <button type="button" className="ghost" onClick={() => setDetail(null)} data-testid="customer-close">{t('close')}</button>
          </header>
          <p data-testid="customer-detail-state">
            <span className={`badge ${detail.customer.active ? 'state-active' : 'state-disabled'}`}>
              {detail.customer.active ? t('accountActive') : t('accountDisabled')}</span>
          </p>
          {detail.customer.active ? (
            confirming ? (
              <div className="confirm" role="alertdialog" aria-labelledby="confirm-text" data-testid="customer-confirm">
                <p id="confirm-text">{t('disableConfirm', { email: detail.customer.email })}</p>
                <div className="row">
                  <button type="button" className="danger-btn" onClick={() => setActive(false)} data-testid="customer-disable-yes">{t('disable')}</button>
                  <button type="button" className="ghost" onClick={() => setConfirming(false)} data-testid="customer-disable-no">{t('cancel')}</button>
                </div>
              </div>
            ) : <button type="button" className="ghost danger-text" onClick={() => setConfirming(true)} data-testid="customer-disable">{t('disableAccount')}</button>
          ) : <button type="button" onClick={() => setActive(true)} data-testid="customer-enable">{t('enableAccount')}</button>}

          <h3>{t('lastAddress')}</h3>
          <p data-testid="customer-address">{detail.lastAddress ? addressLine(detail.lastAddress) : <span className="muted">{t('noRows')}</span>}</p>
          <h3>{t('navOrders')}</h3>
          {detail.orders.length === 0 ? <p className="muted">{t('noRows')}</p> : (
            <table data-testid="customer-orders">
              <thead><tr><th>{t('order')}</th><th>{t('date')}</th><th>{t('status')}</th><th className="num">{t('total')}</th></tr></thead>
              <tbody>{detail.orders.map(o => (
                <tr key={o.id}><td>{o.orderNumber}</td><td className="small">{when(o.createdAt)}</td>
                  <td><span className="badge">{o.status}</span></td><td className="num">{money(o.total)}</td></tr>
              ))}</tbody>
            </table>
          )}
        </section>
      )}
    </section>
  );
}
