import React, { useEffect, useState } from 'react';
import api, { errorText } from '../api';
import { go, saveSession } from '../session';
import { useI18n } from '../i18n';
import Watermark from '../Watermark';

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Same rules as the backend (AuthController: @Email, password 8-72): the server stays the final judge. */
export function validateAuth({ email, password, confirm }, mode) {
  const e = {};
  if (!email.trim()) e.email = 'vEmailRequired';
  else if (!EMAIL.test(email.trim())) e.email = 'vEmailInvalid';
  if (password.length < 8) e.password = 'vPwShort';
  else if (password.length > 72) e.password = 'vPwLong';
  if (mode === 'register' && confirm !== password) e.confirm = 'vPwMatch';
  return e;
}

export default function Login({ redirect = '/', initialMode = 'login' }) {
  const { t } = useI18n();
  const [mode, setMode] = useState(initialMode);
  const [form, setForm] = useState({ email: '', password: '', confirm: '' });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => { setMode(initialMode); setErrors({}); setError(''); }, [initialMode]);

  const set = field => e => {
    const next = { ...form, [field]: e.target.value };
    setForm(next);
    // once a field was flagged, re-check it on every keystroke so the message disappears as soon as it is fixed
    if (errors[field]) setErrors(validateAuth(next, mode));
  };

  const submit = async e => {
    e.preventDefault();
    const found = validateAuth(form, mode);
    setErrors(found);
    if (Object.keys(found).length) { setError(''); return; }
    setBusy(true);
    try {
      const r = await api.post(mode === 'login' ? '/auth/login' : '/auth/register',
        { email: form.email.trim(), password: form.password });
      saveSession(r.data);
      go(['/login', '/register'].includes(redirect) ? '/' : redirect);
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  // guest checkout: buy with just an e-mail; "Forgot password" later turns it into a full account
  const [guestEmail, setGuestEmail] = useState('');
  const [guestError, setGuestError] = useState('');
  const [guestFail, setGuestFail] = useState('');
  const guest = async e => {
    e.preventDefault();
    const v = guestEmail.trim();
    const invalid = !v ? 'vEmailRequired' : !EMAIL.test(v) ? 'vEmailInvalid' : '';
    setGuestError(invalid); setGuestFail('');
    if (invalid) return;
    setBusy(true);
    try {
      saveSession((await api.post('/auth/guest', { email: v })).data);
      go(['/login', '/register'].includes(redirect) ? '/' : redirect);
    } catch (err) {
      setGuestFail(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  const toggle = () => { setMode(mode === 'login' ? 'register' : 'login'); setErrors({}); setError(''); };
  const fieldError = name => errors[name] && (
    <span className="field-error" id={`${name}-error`} data-testid={`${name}-error`}>{t(errors[name])}</span>
  );

  return (
    <div className="auth-page">
    <aside className="auth-hero" data-testid="login-banner" aria-labelledby="auth-hero-title">
      <Watermark tone="dark" />
      <div className="hero-body">
        <p className="hero-mark" aria-hidden="true">R</p>
        <h2 id="auth-hero-title">{t('heroTitle')}</h2>
        <p className="hero-lead">{t('heroLead')}</p>
        <ul className="hero-points">
          <li>{t('heroTrack')}</li>
          <li>{t('heroPay')}</li>
          <li>{t('heroReturn')}</li>
        </ul>
        <p className="hero-tag">RbcTcsWorld</p>
      </div>
    </aside>
    <section className="card auth" data-mode={mode} data-testid="auth-card">
      <h1 data-testid="auth-title">{mode === 'login' ? t('loginTitle') : t('registerTitle')}</h1>
      <p className="muted">{mode === 'login' ? t('loginLead') : t('registerLead')}</p>
      <form onSubmit={submit} noValidate>
        <div className="field">
          <label htmlFor="auth-email">{t('email')}</label>
          <input id="auth-email" data-testid="email-input" type="email" autoComplete="email" value={form.email} onChange={set('email')}
                 aria-invalid={!!errors.email} aria-describedby={errors.email ? 'email-error' : undefined} />
          {fieldError('email')}
        </div>
        <div className="field">
          <label htmlFor="auth-password">{t('password')}</label>
          <input id="auth-password" data-testid="password-input" type={show ? 'text' : 'password'} value={form.password}
                 onChange={set('password')} autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                 aria-invalid={!!errors.password} aria-describedby={errors.password ? 'password-error' : 'password-hint'} />
          {fieldError('password') || <span className="hint" id="password-hint">{t('pwHint')}</span>}
        </div>
        {mode === 'register' && (
          <div className="field">
            <label htmlFor="auth-confirm">{t('confirmPassword')}</label>
            <input id="auth-confirm" data-testid="confirm-input" type={show ? 'text' : 'password'} value={form.confirm}
                   onChange={set('confirm')} autoComplete="new-password" aria-invalid={!!errors.confirm}
                   aria-describedby={errors.confirm ? 'confirm-error' : undefined} />
            {fieldError('confirm')}
          </div>
        )}
        <label className="check">
          <input type="checkbox" checked={show} onChange={e => setShow(e.target.checked)} data-testid="show-password" />
          {t('showPassword')}
        </label>
        {error && <p data-testid="auth-error" className="error" role="alert">{error}</p>}
        <button data-testid="auth-submit" type="submit" disabled={busy} className="block">
          {mode === 'login' ? t('loginBtn') : t('registerBtn')}
        </button>
      </form>
      {mode === 'login' && (
        <button className="link" data-testid="forgot-link" onClick={() => go('/forgot-password')}>{t('forgotLink')}</button>
      )}
      <button className="link" data-testid="auth-toggle" onClick={toggle}>
        {mode === 'login' ? t('toRegister') : t('toLogin')}
      </button>
      {mode === 'login' && (
        <form className="guest-box" onSubmit={guest} noValidate data-testid="guest-form" aria-labelledby="guest-title">
          <h2 id="guest-title">{t('guestTitle')}</h2>
          <p className="hint">{t('guestLead')}</p>
          <div className="field">
            <label htmlFor="guest-email">{t('email')}</label>
            <input id="guest-email" type="email" autoComplete="email" value={guestEmail}
                   onChange={e => { setGuestEmail(e.target.value); if (guestError) setGuestError(''); }}
                   aria-invalid={!!guestError} aria-describedby={guestError ? 'guest-email-error' : undefined} data-testid="guest-email" />
            {guestError && <span className="field-error" id="guest-email-error" data-testid="guest-email-error">{t(guestError)}</span>}
          </div>
          {guestFail && <p className="error" role="alert" data-testid="guest-error">{guestFail}
            {' '}<a href="#/forgot-password">{t('forgotLink')}</a></p>}
          <button type="submit" className="ghost block" disabled={busy} data-testid="guest-continue">{t('guestContinue')}</button>
        </form>
      )}
    </section>
    </div>
  );
}
