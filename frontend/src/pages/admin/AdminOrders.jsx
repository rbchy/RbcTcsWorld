import React, { useEffect, useState } from 'react';
import api, { errorText, money } from '../../api';
import { useI18n } from '../../i18n';
import { Notice, ORDER_STATUSES, when } from './common';
import { addressLine } from '../../usStates';
import { downloadText, toCsv } from '../../csv';

const CARRIERS = ['UPS', 'FEDEX', 'USPS'];
const NO_FILTER = { from: '', to: '', minTotal: '', maxTotal: '', carrier: '', coupon: '' };

/** Date range in order, totals are money (2 decimals) and max not below min. */
export function validateOrderSearch(f) {
  const e = {};
  const money = /^\d+(\.\d{1,2})?$/;
  if (f.from && f.to && f.to < f.from) e.to = 'vEndBeforeStart';
  if (f.minTotal.trim() && !money.test(f.minTotal.trim())) e.minTotal = 'vMoney';
  if (f.maxTotal.trim() && !money.test(f.maxTotal.trim())) e.maxTotal = 'vMoney';
  if (!e.minTotal && !e.maxTotal && f.minTotal.trim() && f.maxTotal.trim() && Number(f.maxTotal) < Number(f.minTotal)) {
    e.maxTotal = 'vMaxBelowMin';
  }
  return e;
}

/** The order matches every filter that is filled in (dates are the day it was placed, local time). */
export function matchesOrder(o, f) {
  const day = o.createdAt ? o.createdAt.slice(0, 10) : '';
  if (f.from && day < f.from) return false;
  if (f.to && day > f.to) return false;
  if (f.minTotal.trim() && Number(o.total) < Number(f.minTotal)) return false;
  if (f.maxTotal.trim() && Number(o.total) > Number(f.maxTotal)) return false;
  if (f.carrier && o.carrier !== f.carrier) return false;
  if (f.coupon === 'yes' && !o.couponCode) return false;
  if (f.coupon === 'no' && o.couponCode) return false;
  return true;
}

export default function AdminOrders() {
  const { t } = useI18n();
  const [rows, setRows] = useState(null);
  const [status, setStatus] = useState('');
  const [q, setQ] = useState('');
  const [carrier, setCarrier] = useState({});
  const [labels, setLabels] = useState({});
  const [ok, setOk] = useState('');
  const [error, setError] = useState('');
  const [adv, setAdv] = useState(NO_FILTER);
  const [applied, setApplied] = useState(NO_FILTER);
  const [advErrors, setAdvErrors] = useState({});
  const [shipsFrom, setShipsFrom] = useState({});          // order id -> allocations (shown on demand)

  // the label bought with the shipping provider (Shippo): fetched on demand, shown as a link to the PDF
  const showLabel = async o => {
    try { setLabels({ ...labels, [o.id]: (await api.get(`/admin/orders/${o.id}/label`)).data }); setError(''); }
    catch (err) { setOk(''); setError(errorText(err)); }
  };

  const toggleShipsFrom = async o => {
    if (shipsFrom[o.id]) { const next = { ...shipsFrom }; delete next[o.id]; setShipsFrom(next); return; }
    try { setShipsFrom({ ...shipsFrom, [o.id]: (await api.get(`/admin/orders/${o.id}/allocations`)).data }); }
    catch (err) { setOk(''); setError(errorText(err)); }
  };

  const load = () => api.get('/admin/orders').then(r => setRows(r.data)).catch(e => setError(errorText(e)));
  useEffect(() => { load(); }, []);

  const act = async (o, action) => {
    try {
      if (action === 'ship') await api.post(`/admin/orders/${o.id}/ship`, { carrier: carrier[o.id] || 'UPS' });
      else await api.post(`/admin/orders/${o.id}/deliver`);
      setOk(action === 'ship' ? `${t('orderShipped')}: ${o.orderNumber}` : `${t('orderDelivered')}: ${o.orderNumber}`);
      setError(''); load();
    } catch (err) { setOk(''); setError(errorText(err)); }
  };

  const needle = q.trim().toLowerCase();
  const shown = (rows || []).slice().reverse()
    .filter(o => !status || o.status === status)
    .filter(o => !needle || o.orderNumber.toLowerCase().includes(needle) || String(o.customerId) === needle)
    .filter(o => matchesOrder(o, applied));

  const setA = k => e => setAdv({ ...adv, [k]: e.target.value });
  const searchAdv = e => {
    e.preventDefault();
    const found = validateOrderSearch(adv);
    setAdvErrors(found);
    if (!Object.keys(found).length) setApplied(adv);
  };
  const exportShown = () => downloadText('orders.csv', toCsv(
    ['order', 'placed', 'customerId', 'status', 'items', 'subtotal', 'discount', 'shipping', 'tax', 'total', 'coupon', 'carrier', 'tracking'],
    shown.map(o => [o.orderNumber, o.createdAt, o.customerId, o.status, o.totalQuantity, Number(o.subtotal), Number(o.discount),
      Number(o.shippingFee), Number(o.tax), Number(o.total), o.couponCode, o.carrier, o.trackingNumber])));
  const field = (k, id, label, type = 'text') => (
    <div className="field" key={k}>
      <label htmlFor={id}>{label}</label>
      <input id={id} type={type} value={adv[k]} onChange={setA(k)} data-testid={id} aria-invalid={!!advErrors[k]}
             inputMode={type === 'text' ? 'decimal' : undefined} aria-describedby={advErrors[k] ? `${id}-error` : undefined} />
      {advErrors[k] && <span className="field-error" id={`${id}-error`} data-testid={`${id}-error`}>{t(advErrors[k])}</span>}
    </div>
  );

  return (
    <section data-testid="admin-orders">
      <header className="page-head">
        <h1>{t('ordersAdminTitle')}</h1>
        <div className="row">
          <input type="search" value={q} onChange={e => setQ(e.target.value)} placeholder={`${t('order')} / ${t('customer')}`}
                 aria-label={`${t('order')} / ${t('customer')}`} data-testid="admin-order-search" />
          <select value={status} onChange={e => setStatus(e.target.value)} aria-label={t('status')} data-testid="admin-order-status">
            <option value="">{t('allStatuses')}</option>
            {ORDER_STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
          </select>
        </div>
      </header>
      <Notice ok={ok} error={error} />
      <form className="card filters" role="search" aria-label={t('advancedSearch')} onSubmit={searchAdv} noValidate
            data-testid="order-adv-search">
        {field('from', 'os-from', t('fromDate'), 'date')}
        {field('to', 'os-to', t('toDate'), 'date')}
        {field('minTotal', 'os-min', t('minTotal'))}
        {field('maxTotal', 'os-max', t('maxTotal'))}
        <label className="field">{t('carrier')}
          <select value={adv.carrier} onChange={setA('carrier')} data-testid="os-carrier">
            <option value="">{t('allCarriers')}</option>
            {CARRIERS.map(c => <option key={c}>{c}</option>)}
          </select>
        </label>
        <label className="field">{t('coupon')}
          <select value={adv.coupon} onChange={setA('coupon')} data-testid="os-coupon">
            <option value="">{t('any')}</option>
            <option value="yes">{t('withCoupon')}</option>
            <option value="no">{t('withoutCoupon')}</option>
          </select>
        </label>
        <div className="filter-actions">
          <button type="submit" data-testid="os-search">{t('search')}</button>
          <button type="button" className="ghost" data-testid="os-reset"
                  onClick={() => { setAdv(NO_FILTER); setAdvErrors({}); setApplied(NO_FILTER); }}>{t('reset')}</button>
          <button type="button" className="ghost" onClick={exportShown} data-testid="os-export" disabled={!rows}>{t('auditExport')}</button>
        </div>
      </form>
      {rows && <p className="small muted" data-testid="order-count">{t('ordersFound', { n: shown.length })}</p>}
      {!rows ? <p>{t('loading')}</p> : (
        <div className="table-wrap card">
          <table data-testid="admin-order-table">
            <thead><tr><th>{t('order')}</th><th>{t('date')}</th><th>{t('customer')}</th><th>{t('status')}</th>
              <th className="num">{t('total')}</th><th>{t('actions')}</th></tr></thead>
            <tbody>
              {shown.length === 0 && <tr><td colSpan="6" className="muted">{t('noRows')}</td></tr>}
              {shown.map(o => (
                <tr key={o.id} data-testid="admin-order-row" data-order-id={o.id}>
                  <td>{o.orderNumber}</td>
                  <td className="small">{when(o.createdAt)}</td>
                  <td>#{o.customerId}
                    {o.shippingAddress && <div className="small muted" data-testid="aor-ship-to">{addressLine(o.shippingAddress)}</div>}</td>
                  <td><span className={`badge ${o.status}`} data-testid="aor-status">{o.status}</span>
                    <div><button type="button" className="link small" onClick={() => toggleShipsFrom(o)} data-testid="aor-ships-from"
                      aria-expanded={!!shipsFrom[o.id]}>{t('shipsFrom')}</button></div>
                    {shipsFrom[o.id] && (
                      <div className="small muted" data-testid="aor-allocations">
                        {shipsFrom[o.id].length === 0 ? t('noRows')
                          : shipsFrom[o.id].map(a => `${a.warehouseCode} ×${a.quantity}`).join(', ')}
                      </div>
                    )}
                    {o.trackingNumber && <div className="small muted">{o.carrier} {o.trackingNumber}</div>}
                    {o.trackingNumber && !labels[o.id] && (
                      <div><button type="button" className="link small" onClick={() => showLabel(o)} data-testid="aor-label">
                        {t('shippingLabel')}</button></div>
                    )}
                    {labels[o.id] && (
                      <div className="small"><a href={labels[o.id].labelUrl} target="_blank" rel="noopener noreferrer"
                        data-testid="aor-label-link">{t('printLabel')} ({money(labels[o.id].cost)})</a></div>
                    )}</td>
                  <td className="num">{money(o.total)}</td>
                  <td className="actions">
                    {o.status === 'PAID' && (
                      <>
                        <select aria-label={`${t('carrier')} ${o.orderNumber}`} value={carrier[o.id] || 'UPS'}
                                onChange={e => setCarrier({ ...carrier, [o.id]: e.target.value })} data-testid="aor-carrier">
                          {CARRIERS.map(c => <option key={c}>{c}</option>)}
                        </select>
                        <button onClick={() => act(o, 'ship')} data-testid="aor-ship">{t('ship')}</button>
                      </>
                    )}
                    {o.status === 'SHIPPED' && <button onClick={() => act(o, 'deliver')} data-testid="aor-deliver">{t('deliver')}</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
