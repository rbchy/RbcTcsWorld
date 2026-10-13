import React, { useState } from 'react';
import { useI18n } from '../i18n';

/**
 * Shown after Exit. The session is already gone; the browser was asked to close the window. When it refuses
 * (a normal tab the user opened), this page says how to close it and how to stop a shop running on this computer.
 */
export default function Goodbye({ session }) {
  const { t } = useI18n();
  const [stillOpen, setStillOpen] = useState(false);
  const close = () => {
    window.close();
    setTimeout(() => setStillOpen(true), 300);   // still here: the browser did not allow it
  };
  const mac = /Mac|iPhone|iPad/.test(navigator.platform || navigator.userAgent);
  return (
    <section className="card narrow goodbye" data-testid="goodbye" aria-labelledby="goodbye-title">
      <svg className="goodbye-mark" viewBox="0 0 120 80" aria-hidden="true" focusable="false">
        <rect x="18" y="24" width="84" height="48" rx="4" fill="#c7a77a" />
        <path d="M18 24l42-16 42 16-42 14z" fill="#dcc29a" />
        <path d="M44 30l18 8 18-8" stroke="#fffdf6" strokeWidth="6" fill="none" />
        <path d="M46 54l10 10 20-22" stroke="#12313f" strokeWidth="6" fill="none" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      <h1 id="goodbye-title">{t('exitTitle')}</h1>
      {!session && <p data-testid="goodbye-signed-out">{t('exitSignedOut')}</p>}
      <p>{t('exitCloseTab', { keys: mac ? '⌘ W' : 'Ctrl + W' })}</p>
      {stillOpen && <p className="hint" role="status" data-testid="goodbye-still-open">{t('exitBrowserRefused')}</p>}
      <div className="row">
        <button onClick={close} data-testid="goodbye-close">{t('exitCloseWindow')}</button>
        <a className="button-link" href="#/" data-testid="goodbye-back">{t('exitBack')}</a>
      </div>
      <p className="hint">{t('exitStopServer')}</p>
    </section>
  );
}
