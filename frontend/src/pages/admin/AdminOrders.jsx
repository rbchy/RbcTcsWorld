import React, { useEffect, useState } from 'react';
import api, { errorText, money } from '../../api';
import { useI18n } from '../../i18n';
import { Notice, ORDER_STATUSES, when } from './common';

const CARRIERS = ['UPS', 'FEDEX', 'USPS'];

export default function AdminOrders() {
  const { t } = useI18n();
  const [rows, setRows] = useState(null);
  const [status, setStatus] = useState('');
  const [q, setQ] = useState('');
  const [carrier, setCarrier] = useState({});
  const [ok, setOk] = useState('');
  const [error, setError] = useState('');

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
    .filter(o => !needle || o.orderNumber.toLowerCase().includes(needle) || String(o.customerId) === needle);

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
                  <td>#{o.customerId}</td>
                  <td><span className={`badge ${o.status}`} data-testid="aor-status">{o.status}</span>
                    {o.trackingNumber && <div className="small muted">{o.carrier} {o.trackingNumber}</div>}</td>
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
