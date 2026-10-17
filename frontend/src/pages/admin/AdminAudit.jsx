import React, { useEffect, useState } from 'react';
import api, { errorText } from '../../api';
import { useI18n } from '../../i18n';
import { Notice, when } from './common';

/**
 * The audit trail (read-only): who changed which product or order, when, from what to what.
 * Rows cannot be edited or deleted - not here, not in the API, not in the database.
 * "Check integrity" recomputes the hash chain; "Download CSV" exports the rows that match the filter.
 */
export default function AdminAudit() {
  const { t } = useI18n();
  const [filter, setFilter] = useState({ entityType: '', entityId: '', actor: '' });
  const [applied, setApplied] = useState(filter);
  const [rows, setRows] = useState(null);
  const [check, setCheck] = useState(null);
  const [error, setError] = useState('');

  const params = f => ({
    entityType: f.entityType || undefined,
    entityId: f.entityId.trim() || undefined,
    actor: f.actor.trim() || undefined,
  });

  useEffect(() => {
    api.get('/admin/audit', { params: params(applied) })
      .then(r => { setRows(r.data); setError(''); }).catch(e => setError(errorText(e)));
  }, [applied]);

  const show = e => { e.preventDefault(); setApplied(filter); };

  const verify = () => api.get('/admin/audit/verify')
    .then(r => { setCheck(r.data); setError(''); }).catch(e => setError(errorText(e)));

  const download = async () => {
    try {
      const r = await api.get('/admin/audit/export', { params: params(applied), responseType: 'blob' });
      const url = URL.createObjectURL(r.data);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'audit-trail.csv';
      document.body.appendChild(a);   // Firefox only downloads links that are in the page
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 10000);
    } catch (err) { setError(errorText(err)); }
  };

  const set = key => e => setFilter({ ...filter, [key]: e.target.value });
  const value = v => (v == null ? <span className="muted">-</span> : v);

  return (
    <section data-testid="admin-audit">
      <header className="page-head">
        <h1>{t('auditTitle')}</h1>
        <div className="row">
          <button type="button" onClick={verify} data-testid="audit-verify">{t('auditVerify')}</button>
          <button type="button" onClick={download} data-testid="audit-export">{t('auditExport')}</button>
        </div>
      </header>
      <p className="muted small">{t('auditHint')}</p>

      {check && (check.valid
        ? <p className="ok" role="status" data-testid="audit-check">{t('auditValid', { n: check.entries })}</p>
        : <p className="error" role="alert" data-testid="audit-check">
            {t('auditBroken', { seq: check.brokenAtSeq })} ({check.reason})</p>)}
      <Notice error={error} />

      <form className="card row" onSubmit={show} data-testid="audit-filter">
        <label className="field">{t('auditRecord')}
          <select value={filter.entityType} onChange={set('entityType')} data-testid="audit-type">
            <option value="">{t('auditAllRecords')}</option>
            <option value="PRODUCT">{t('auditProduct')}</option>
            <option value="ORDER">{t('auditOrder')}</option>
          </select>
        </label>
        <label className="field">{t('auditId')}
          <input inputMode="numeric" pattern="[0-9]*" value={filter.entityId} onChange={set('entityId')}
            data-testid="audit-id" />
        </label>
        <label className="field">{t('auditActor')}
          <input type="email" value={filter.actor} onChange={set('actor')} data-testid="audit-actor" />
        </label>
        <button type="submit" data-testid="audit-show">{t('auditShow')}</button>
      </form>

      {!rows ? <p>{t('loading')}</p> : (
        <div className="table-wrap card" tabIndex={0} role="region" aria-label={t('auditTitle')}>
          <table data-testid="audit-table">
            <thead><tr>
              <th>#</th><th>{t('date')}</th><th>{t('auditActor')}</th><th>{t('auditRecord')}</th>
              <th>{t('auditChange')}</th><th>{t('auditOld')}</th><th>{t('auditNew')}</th>
            </tr></thead>
            <tbody>
              {rows.length === 0 && <tr><td colSpan="7" className="muted">{t('noRows')}</td></tr>}
              {rows.map(r => (
                <tr key={r.seq} data-testid="audit-row" data-seq={r.seq}>
                  <td className="small" title={r.hash}>{r.seq}</td>
                  <td className="small">{when(r.occurredAt)}</td>
                  <td className="small" data-testid="audit-row-actor">{r.actor}</td>
                  <td>{r.entityType === 'ORDER' ? t('auditOrder') : t('auditProduct')} #{r.entityId}</td>
                  <td className="small" data-testid="audit-row-field">{r.action} · {r.field}</td>
                  <td data-testid="audit-row-old">{value(r.oldValue)}</td>
                  <td data-testid="audit-row-new">{value(r.newValue)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
