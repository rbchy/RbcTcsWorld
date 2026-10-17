import React, { useState } from 'react';
import api, { errorText } from '../api';
import { go } from '../session';
import { useI18n } from '../i18n';
import { validateAuth } from './Login';

/**
 * "Forgot password": ask for a link by e-mail. The answer is the same whether or not the e-mail has an
 * account (the shop never says who is a customer), so the page always shows the same confirmation.
 */
export function ForgotPassword() {
  const { t } = useI18n();
  const [email, setEmail] = useState('');
  const [fieldError, setFieldError] = useState('');
  const [error, setError] = useState('');
  const [sent, setSent] = useState(false);

  const submit = async e => {
    e.preventDefault();
    const found = validateAuth({ email, password: 'xxxxxxxx', confirm: 'xxxxxxxx' }, 'login').email;
    setFieldError(found || '');
    if (found) return;
    try {
      await api.post('/auth/forgot-password', { email: email.trim() });
      setSent(true);
      setError('');
    } catch (err) {
      setError(errorText(err));
    }
  };

  return (
    <section className="card narrow auth" data-testid="forgot-card">
      <h1>{t('forgotTitle')}</h1>
      {sent ? <p className="ok" role="status" data-testid="forgot-sent">{t('forgotSent')}</p> : (
        <form onSubmit={submit} noValidate>
          <p className="muted">{t('forgotLead')}</p>
          <div className="field">
            <label htmlFor="forgot-email">{t('email')}</label>
            <input id="forgot-email" data-testid="forgot-email" type="email" autoComplete="email" value={email}
                   onChange={e => setEmail(e.target.value)} aria-invalid={!!fieldError}
                   aria-describedby={fieldError ? 'forgot-email-error' : undefined} />
            {fieldError && <span className="field-error" id="forgot-email-error" data-testid="forgot-email-error">{t(fieldError)}</span>}
          </div>
          {error && <p className="error" role="alert" data-testid="forgot-error">{error}</p>}
          <button type="submit" className="block" data-testid="forgot-submit">{t('forgotSend')}</button>
        </form>
      )}
      <button className="link" data-testid="forgot-to-login" onClick={() => go('/login')}>{t('backToLogin')}</button>
    </section>
  );
}

/** The page the e-mailed link opens: #/reset-password?token=... */
export function ResetPassword({ token }) {
  const { t } = useI18n();
  const [form, setForm] = useState({ password: '', confirm: '' });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);

  const submit = async e => {
    e.preventDefault();
    const found = validateAuth({ email: 'x@x.xx', password: form.password, confirm: form.confirm }, 'register');
    setErrors(found);
    if (Object.keys(found).length) { setError(''); return; }
    try {
      await api.post('/auth/reset-password', { token, newPassword: form.password });
      setDone(true);
      setError('');
    } catch (err) {
      setError(errorText(err));
    }
  };

  const set = f => e => setForm({ ...form, [f]: e.target.value });
  const fieldError = name => errors[name] && (
    <span className="field-error" id={`reset-${name}-error`} data-testid={`reset-${name}-error`}>{t(errors[name])}</span>
  );

  return (
    <section className="card narrow auth" data-testid="reset-card">
      <h1>{t('resetTitle')}</h1>
      {!token && <p className="error" role="alert" data-testid="reset-error">{t('resetNoToken')}</p>}
      {done ? <p className="ok" role="status" data-testid="reset-done">{t('resetDone')}</p> : token && (
        <form onSubmit={submit} noValidate>
          <div className="field">
            <label htmlFor="reset-password">{t('newPassword')}</label>
            <input id="reset-password" data-testid="reset-password" type="password" autoComplete="new-password"
                   value={form.password} onChange={set('password')} aria-invalid={!!errors.password}
                   aria-describedby={errors.password ? 'reset-password-error' : 'reset-password-hint'} />
            {fieldError('password') || <span className="hint" id="reset-password-hint">{t('pwHint')}</span>}
          </div>
          <div className="field">
            <label htmlFor="reset-confirm">{t('confirmPassword')}</label>
            <input id="reset-confirm" data-testid="reset-confirm" type="password" autoComplete="new-password"
                   value={form.confirm} onChange={set('confirm')} aria-invalid={!!errors.confirm}
                   aria-describedby={errors.confirm ? 'reset-confirm-error' : undefined} />
            {fieldError('confirm')}
          </div>
          {error && <p className="error" role="alert" data-testid="reset-error">{error}</p>}
          <button type="submit" className="block" data-testid="reset-submit">{t('resetSave')}</button>
        </form>
      )}
      {(done || !token || error) && (
        <button className="link" data-testid="reset-to-login" onClick={() => go('/login')}>{t('backToLogin')}</button>
      )}
    </section>
  );
}
