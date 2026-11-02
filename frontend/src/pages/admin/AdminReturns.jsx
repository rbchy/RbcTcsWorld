import React, { useEffect, useState } from 'react';
import api, { errorText, money } from '../../api';
import { useI18n } from '../../i18n';
import { Notice, when } from './common';

export default function AdminReturns() {
  const { t } = useI18n();
  const [rows, setRows] = useState(null);
  const [status, setStatus] = useState('REQUESTED');
  const [note, setNote] = useState({});
  const [ok, setOk] = useState('');
  const [error, setError] = useState('');

  const load = () => api.get('/admin/returns', { params: { status: status || undefined } })
    .then(r => setRows(r.data)).catch(e => setError(errorText(e)));
  useEffect(() => { load(); }, [status]);

  const decide = async (r, action) => {
    try {
      await api.post(`/admin/returns/${r.id}/${action}`, { note: note[r.id] || null });
      setOk(action === 'approve' ? t('returnApproved') : t('returnRejected')); setError(''); load();
    } catch (err) { setOk(''); setError(errorText(err)); }
  };

  /** A prepaid label from the customer back to the store (ADR-0023); the customer gets an e-mail. */
  const [buying, setBuying] = useState(null);
  const buyLabel = async r => {
    setBuying(r.id);
    try {
      await api.post(`/admin/returns/${r.id}/label`);
      setOk(t('returnLabelBought')); setError(''); load();
    } catch (err) { setOk(''); setError(errorText(err)); }
    finally { setBuying(null); }
  };

  return (
    <section data-testid="admin-returns">
      <header className="page-head">
        <h1>{t('returnsTitle')}</h1>
        <select value={status} onChange={e => setStatus(e.target.value)} aria-label={t('status')} data-testid="admin-return-status">
          <option value="">{t('allStatuses')}</option>
          {['REQUESTED', 'APPROVED', 'REJECTED'].map(s => <option key={s}>{s}</option>)}
        </select>
      </header>
      <Notice ok={ok} error={error} />
      {!rows ? <p>{t('loading')}</p> : (
        <div className="table-wrap card">
          <table data-testid="admin-return-table">
            <thead><tr><th>{t('order')}</th><th>{t('date')}</th><th>{t('reason')}</th><th>{t('status')}</th>
              <th className="num">{t('refund')}</th><th>{t('actions')}</th></tr></thead>
            <tbody>
              {rows.length === 0 && <tr><td colSpan="6" className="muted">{t('noRows')}</td></tr>}
              {rows.map(r => (
                <tr key={r.id} data-testid="admin-return-row" data-order-id={r.orderId}>
                  <td>#{r.orderId}</td>
                  <td className="small">{when(r.createdAt)}</td>
                  <td>{r.reason}{r.comment && <div className="small muted">{r.comment}</div>}</td>
                  <td><span className="badge">{r.status}</span>{r.adminNote && <div className="small muted">{r.adminNote}</div>}</td>
                  <td className="num">{r.refundAmount != null ? money(r.refundAmount) : ''}</td>
                  <td className="actions">
                    {r.status === 'REQUESTED' && (
                      <>
                        <input value={note[r.id] || ''} onChange={e => setNote({ ...note, [r.id]: e.target.value })} maxLength={255}
                               aria-label={`Note #${r.orderId}`} placeholder="Note" className="note" />
                        <button onClick={() => decide(r, 'approve')} data-testid="arr-approve">{t('approve')}</button>
                        <button className="ghost" onClick={() => decide(r, 'reject')} data-testid="arr-reject">{t('reject')}</button>
                      </>
                    )}
                    {r.status === 'APPROVED' && (r.returnLabelUrl ? (
                      <a href={r.returnLabelUrl} target="_blank" rel="noopener noreferrer" data-testid="arr-label-link">
                        {t('returnLabelOpen', { carrier: r.returnCarrier })}</a>
                    ) : (
                      <button className="ghost" onClick={() => buyLabel(r)} disabled={buying === r.id} data-testid="arr-buy-label">
                        {buying === r.id ? t('loading') : t('returnLabelBuy')}</button>
                    ))}
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
