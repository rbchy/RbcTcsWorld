import React, { useEffect, useState } from 'react';
import api, { errorText } from '../api';
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
    </section>
  );
}
