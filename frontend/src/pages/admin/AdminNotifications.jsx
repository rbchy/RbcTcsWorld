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
      <BlockedAddresses t={t} />
    </section>
  );
}

/**
 * Addresses the shop no longer e-mails after a hard bounce or a spam complaint (ADR-0014). "Allow again" when the
 * customer has fixed the mailbox; if it bounces again, Amazon SES puts it back on the list.
 */
function BlockedAddresses({ t }) {
  const [rows, setRows] = useState(null);
  const [search, setSearch] = useState('');
  const [confirm, setConfirm] = useState(null);
  const [ok, setOk] = useState('');
  const [error, setError] = useState('');

  const load = () => api.get('/admin/email-suppressions', { params: { search: search.trim() || undefined } })
    .then(r => setRows(r.data)).catch(e => setError(errorText(e)));
  useEffect(() => {
    const timer = setTimeout(load, search ? 250 : 0);
    return () => clearTimeout(timer);
  }, [search]);

  const lift = async row => {
    try {
      await api.delete('/admin/email-suppressions', { params: { email: row.email } });
      setOk(t('suppressionLifted', { email: row.email })); setError(''); setConfirm(null); load();
    } catch (err) { setOk(''); setError(errorText(err)); }
  };

  return (
    <section className="blocked-addresses" aria-labelledby="blocked-title" data-testid="admin-suppressions">
      <header className="page-head">
        <h2 id="blocked-title">{t('suppressionsTitle')}</h2>
        <input type="search" value={search} onChange={e => setSearch(e.target.value)} placeholder={t('email')}
               aria-label={t('suppressionsSearch')} data-testid="admin-suppression-search" />
      </header>
      <p className="muted small">{t('suppressionsHint')}</p>
      <Notice ok={ok} error={error} />
      {!rows ? <p>{t('loading')}</p> : (
        <div className="table-wrap card" tabIndex={0} role="region" aria-label={t('suppressionsTitle')}>
          <table data-testid="admin-suppression-table">
            <thead><tr><th>{t('email')}</th><th>{t('suppressionReason')}</th><th>{t('date')}</th><th>{t('actions')}</th></tr></thead>
            <tbody>
              {rows.length === 0 && <tr><td colSpan="4" className="muted">{t('noRows')}</td></tr>}
              {rows.map(r => (
                <tr key={r.email} data-testid="admin-suppression-row" data-email={r.email}>
                  <td className="small">{r.email}</td>
                  <td><span className="badge">{r.reason === 'COMPLAINT' ? t('suppressionComplaint') : t('suppressionBounce')}</span>
                    {r.detail && <div className="small muted">{r.detail}</div>}</td>
                  <td className="small">{when(r.createdAt)}</td>
                  <td className="actions">
                    {confirm === r.email ? (
                      <>
                        <span className="small">{r.reason === 'COMPLAINT' ? t('suppressionConfirmComplaint') : t('suppressionConfirm')}</span>{' '}
                        <button onClick={() => lift(r)} data-testid="asp-lift-yes">{t('suppressionAllow')}</button>{' '}
                        <button className="ghost" onClick={() => setConfirm(null)}>{t('cancel')}</button>
                      </>
                    ) : (
                      <button className="ghost" onClick={() => { setOk(''); setConfirm(r.email); }} data-testid="asp-lift">{t('suppressionAllow')}</button>
                    )}
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
