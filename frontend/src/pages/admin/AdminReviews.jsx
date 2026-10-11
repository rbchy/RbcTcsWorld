import React, { useEffect, useState } from 'react';
import api, { errorText } from '../../api';
import { useI18n } from '../../i18n';
import { Notice, when } from './common';

export default function AdminReviews() {
  const { t } = useI18n();
  const [rows, setRows] = useState(null);
  const [status, setStatus] = useState('');
  const [ok, setOk] = useState('');
  const [error, setError] = useState('');

  const load = () => api.get('/admin/reviews', { params: { status: status || undefined } })
    .then(r => setRows(r.data)).catch(e => setError(errorText(e)));
  useEffect(() => { load(); }, [status]);

  const moderate = async (r, action) => {
    try {
      await api.post(`/admin/reviews/${r.id}/${action}`);
      setOk(action === 'hide' ? t('reviewHidden') : t('reviewPublished')); setError(''); load();
    } catch (err) { setOk(''); setError(errorText(err)); }
  };

  return (
    <section data-testid="admin-reviews">
      <header className="page-head">
        <h1>{t('reviewsTitle')}</h1>
        <select value={status} onChange={e => setStatus(e.target.value)} aria-label={t('status')} data-testid="admin-review-status">
          <option value="">{t('allStatuses')}</option>
          <option>PUBLISHED</option>
          <option>HIDDEN</option>
        </select>
      </header>
      <Notice ok={ok} error={error} />
      {!rows ? <p>{t('loading')}</p> : (
        <div className="table-wrap card">
          <table data-testid="admin-review-table">
            <thead><tr><th>{t('product')}</th><th>{t('rating')}</th><th>{t('review')}</th><th>{t('status')}</th><th>{t('actions')}</th></tr></thead>
            <tbody>
              {rows.length === 0 && <tr><td colSpan="5" className="muted">{t('noRows')}</td></tr>}
              {rows.map(r => (
                <tr key={r.id} data-testid="admin-review-row" data-review-id={r.id}>
                  <td><a href={`#/products/${r.productId}`}>#{r.productId}</a></td>
                  <td><span aria-label={`${r.rating} / 5`}>{'★'.repeat(r.rating)}{'☆'.repeat(5 - r.rating)}</span></td>
                  <td><strong>{r.title}</strong><div className="small">{r.body}</div>
                    <div className="small muted">{r.reviewer} · {when(r.createdAt)}</div></td>
                  <td><span className="badge" data-testid="arv-status">{r.status}</span></td>
                  <td className="actions">
                    {r.status === 'PUBLISHED'
                      ? <button className="ghost" onClick={() => moderate(r, 'hide')} data-testid="arv-hide">{t('hide')}</button>
                      : <button onClick={() => moderate(r, 'publish')} data-testid="arv-publish">{t('publish')}</button>}
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
