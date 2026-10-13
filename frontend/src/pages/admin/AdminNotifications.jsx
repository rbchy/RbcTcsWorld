import React, { useEffect, useState } from 'react';
import api, { errorText } from '../../api';
import { useI18n } from '../../i18n';
import { Notice, when } from './common';

/** The e-mail outbox: who was told what, whether it went out, and "send again" for failed ones. */
export default function AdminNotifications() {
  const { t } = useI18n();
  const [rows, setRows] = useState(null);
  const [status, setStatus] = useState('');
  const [ok, setOk] = useState('');
  const [error, setError] = useState('');

  const load = () => api.get('/admin/notifications', { params: { status: status || undefined } })
    .then(r => setRows(r.data)).catch(e => setError(errorText(e)));
  useEffect(() => { load(); }, [status]);

  const retry = async n => {
    try { await api.post(`/admin/notifications/${n.id}/retry`); setOk(t('emailQueued')); setError(''); load(); }
    catch (err) { setOk(''); setError(errorText(err)); }
  };

  return (
    <section data-testid="admin-notifications">
      <header className="page-head">
        <h1>{t('notificationsTitle')}</h1>
        <select value={status} onChange={e => setStatus(e.target.value)} aria-label={t('status')} data-testid="admin-notification-status">
          <option value="">{t('allStatuses')}</option>
          <option>PENDING</option>
          <option>SENT</option>
          <option>FAILED</option>
        </select>
      </header>
      <p className="muted small">{t('notificationsHint')}</p>
      <Notice ok={ok} error={error} />
      {!rows ? <p>{t('loading')}</p> : (
        // focusable + named: on a phone the table scrolls sideways, keyboard users must be able to reach it
        <div className="table-wrap card" tabIndex={0} role="region" aria-label={t('notificationsTitle')}>
          <table data-testid="admin-notification-table">
            <thead><tr><th>{t('order')}</th><th>{t('recipient')}</th><th>{t('subject')}</th><th>{t('status')}</th><th>{t('date')}</th><th>{t('actions')}</th></tr></thead>
            <tbody>
              {rows.length === 0 && <tr><td colSpan="6" className="muted">{t('noRows')}</td></tr>}
              {rows.map(n => (
                <tr key={n.id} data-testid="admin-notification-row" data-notification-id={n.id}>
                  <td>{n.orderId ? `#${n.orderId}` : '-'}</td>
                  <td className="small">{n.recipient}</td>
                  <td>{n.subject}{n.lastError && <div className="small error-text">{n.lastError}</div>}</td>
                  <td><span className="badge" data-testid="anf-status">{n.status}</span>
                    {n.attempts > 1 && <div className="small muted">{t('attempts', { n: n.attempts })}</div>}</td>
                  <td className="small">{when(n.sentAt || n.createdAt)}</td>
                  <td className="actions">
                    {n.status === 'FAILED' && <button onClick={() => retry(n)} data-testid="anf-retry">{t('sendAgain')}</button>}
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
