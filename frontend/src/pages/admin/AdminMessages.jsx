import React, { useEffect, useState } from 'react';
import api, { errorText } from '../../api';
import { useI18n } from '../../i18n';
import { Notice, when } from './common';

/** Messages from the contact form (ADR-0018): read, answer once (e-mailed to the sender), close. */
export default function AdminMessages() {
  const { t } = useI18n();
  const [rows, setRows] = useState(null);
  const [status, setStatus] = useState('NEW');
  const [open, setOpen] = useState(null);        // id of the message being answered
  const [reply, setReply] = useState('');
  const [replyError, setReplyError] = useState('');
  const [ok, setOk] = useState('');
  const [error, setError] = useState('');

  const load = () => api.get('/admin/messages', { params: { status: status || undefined } })
    .then(r => setRows(r.data)).catch(e => setError(errorText(e)));
  useEffect(() => { load(); }, [status]);

  const send = async m => {
    const text = reply.trim();
    if (text.length < 2) { setReplyError(t('vRequired')); return; }
    try {
      await api.post(`/admin/messages/${m.id}/reply`, { text });
      setOk(t('messageAnswered', { ticket: m.ticket })); setError(''); setOpen(null); setReply(''); load();
    } catch (err) { setOk(''); setError(errorText(err)); }
  };

  const close = async m => {
    try { await api.post(`/admin/messages/${m.id}/close`); setOk(t('messageClosed', { ticket: m.ticket })); setError(''); load(); }
    catch (err) { setOk(''); setError(errorText(err)); }
  };

  return (
    <section data-testid="admin-messages">
      <header className="page-head">
        <h1>{t('messagesTitle')}</h1>
        <select value={status} onChange={e => setStatus(e.target.value)} aria-label={t('status')} data-testid="admin-message-status">
          <option value="">{t('allStatuses')}</option>
          <option value="NEW">{t('msgNEW')}</option>
          <option value="ANSWERED">{t('msgANSWERED')}</option>
          <option value="CLOSED">{t('msgCLOSED')}</option>
        </select>
      </header>
      <Notice ok={ok} error={error} />
      {!rows ? <p>{t('loading')}</p> : rows.length === 0 ? <p className="card muted" data-testid="admin-messages-empty">{t('noRows')}</p> : (
        <ul className="message-list">
          {rows.map(m => (
            <li key={m.id} className="card" data-testid="admin-message" data-ticket={m.ticket}>
              <p className="row space">
                <strong>{m.ticket} · {t(`topic${m.topic}`)}</strong>
                <span className={`badge msg-${m.status}`} data-testid="am-status">{t(`msg${m.status}`)}</span>
              </p>
              <p className="small muted">
                {m.name} &lt;<a href={`mailto:${m.email}`}>{m.email}</a>&gt; · {m.customer ? t('msgCustomer') : t('msgVisitor')}
                {m.orderNumber && <> · {t('order')} {m.orderNumber}</>} · {when(m.createdAt)}
              </p>
              <p className="message-text" data-testid="am-text">{m.message}</p>
              {m.reply && (
                <blockquote className="message-reply" data-testid="am-reply">
                  {m.reply}
                  <span className="small muted"> — {m.answeredBy}, {when(m.answeredAt)}</span>
                </blockquote>
              )}
              {open === m.id ? (
                <div className="field">
                  <label htmlFor={`am-answer-${m.id}`}>{t('msgYourAnswer')}</label>
                  <textarea id={`am-answer-${m.id}`} rows={5} maxLength={4000} value={reply}
                            onChange={e => { setReply(e.target.value); setReplyError(''); }}
                            aria-invalid={!!replyError} data-testid="am-answer" />
                  {replyError && <span className="field-error" data-testid="am-answer-error">{replyError}</span>}
                  <span className="hint">{t('msgAnswerHint')}</span>
                  <p className="row">
                    <button onClick={() => send(m)} data-testid="am-send">{t('msgSendAnswer')}</button>
                    <button className="ghost" onClick={() => { setOpen(null); setReply(''); }}>{t('cancel')}</button>
                  </p>
                </div>
              ) : (
                <p className="row">
                  {m.status === 'NEW' && <button onClick={() => { setOpen(m.id); setReply(''); setReplyError(''); }} data-testid="am-reply-open">{t('msgAnswer')}</button>}
                  {m.status !== 'CLOSED' && <button className="ghost" onClick={() => close(m)} data-testid="am-close">{t('close')}</button>}
                </p>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
