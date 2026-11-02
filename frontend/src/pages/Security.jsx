import React, { useEffect, useState } from 'react';
import api, { errorText, logout } from '../api';
import { useI18n } from '../i18n';

/** Two-factor sign-in on or off: needs the current password (REQ-AUTH-16). */
export default function Security() {
  const { t } = useI18n();
  const [enabled, setEnabled] = useState(null);
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => { api.get('/account/two-factor').then(r => setEnabled(r.data.enabled)).catch(e => setError(errorText(e))); }, []);

  const change = async e => {
    e.preventDefault();
    if (!password) { setError(t('vPwShort')); return; }
    setBusy(true); setError(''); setOk('');
    try {
      const r = await api.put('/account/two-factor', { enabled: !enabled, password });
      setEnabled(r.data.enabled);
      setOk(t(r.data.enabled ? 'twoFactorOn' : 'twoFactorOff'));
      setPassword('');
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card security" data-testid="security-page" aria-labelledby="security-title">
      <h1 id="security-title">{t('navSecurity')}</h1>
      <p className="muted">{t('twoFactorLead')}</p>
      {enabled !== null && (
        <p><strong data-testid="two-factor-state">{enabled ? t('twoFactorIsOn') : t('twoFactorIsOff')}</strong></p>
      )}
      <form onSubmit={change} noValidate>
        <div className="field">
          <label htmlFor="sec-password">{t('password')}</label>
          <input id="sec-password" type="password" autoComplete="current-password" value={password}
                 onChange={e => setPassword(e.target.value)} data-testid="security-password" />
          <span className="hint">{t('twoFactorNeedsPassword')}</span>
        </div>
        {error && <p className="error" role="alert" data-testid="security-error">{error}</p>}
        {ok && <p className="success" role="status" data-testid="security-ok">{ok}</p>}
        <button type="submit" disabled={busy || enabled === null} data-testid="two-factor-toggle">
          {enabled ? t('twoFactorTurnOff') : t('twoFactorTurnOn')}
        </button>
      </form>
      <DeleteAccount t={t} />
    </section>
  );
}

/**
 * The customer deletes their own account (REQ-AUTH-17, ADR-0022): the password, a typed confirmation word, then the
 * session ends here. Orders stay with the shop for tax law; the page says so before anything happens.
 */
function DeleteAccount({ t }) {
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState('');
  const [word, setWord] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const confirmWord = t('deleteAccountWord');

  const submit = async e => {
    e.preventDefault();
    if (!password) { setError(t('vPwShort')); return; }
    if (word.trim().toUpperCase() !== confirmWord.toUpperCase()) { setError(t('deleteAccountTypeWord', { word: confirmWord })); return; }
    setBusy(true); setError('');
    try {
      await api.post('/account/delete', { password });
      await logout();
      try { sessionStorage.setItem('accountDeleted', '1'); } catch { /* storage blocked: the page still works */ }
      window.location.hash = '#/goodbye';
    } catch (err) {
      setError(errorText(err));
      setBusy(false);
    }
  };

  return (
    <section className="delete-account" aria-labelledby="delete-account-title" data-testid="delete-account">
      <h2 id="delete-account-title">{t('deleteAccountTitle')}</h2>
      <p className="muted">{t('deleteAccountLead')}</p>
      {!open ? (
        <button type="button" className="danger-btn" onClick={() => setOpen(true)} data-testid="delete-account-open">
          {t('deleteAccountTitle')}
        </button>
      ) : (
        <form onSubmit={submit} noValidate>
          <div className="field">
            <label htmlFor="del-password">{t('password')}</label>
            <input id="del-password" type="password" autoComplete="current-password" value={password}
                   onChange={e => setPassword(e.target.value)} data-testid="delete-account-password" />
          </div>
          <div className="field">
            <label htmlFor="del-word">{t('deleteAccountTypeWord', { word: confirmWord })}</label>
            <input id="del-word" value={word} onChange={e => setWord(e.target.value)} autoComplete="off"
                   data-testid="delete-account-word" />
          </div>
          {error && <p className="error" role="alert" data-testid="delete-account-error">{error}</p>}
          <button type="submit" className="danger-btn" disabled={busy} data-testid="delete-account-confirm">
            {t('deleteAccountConfirm')}
          </button>{' '}
          <button type="button" className="ghost" onClick={() => { setOpen(false); setError(''); }}>{t('cancel')}</button>
        </form>
      )}
    </section>
  );
}
