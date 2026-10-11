import React from 'react';
import { useI18n } from '../../i18n';

export const ORDER_STATUSES = ['PLACED', 'PAID', 'SHIPPED', 'DELIVERED', 'RETURN_REQUESTED', 'RETURNED', 'CANCELLED'];

/** Success / error line under a page title. role=status/alert so screen readers announce it. */
export function Notice({ ok, error }) {
  return (
    <>
      {ok && <p className="ok" role="status" data-testid="admin-notice">{ok}</p>}
      {error && <p className="error" role="alert" data-testid="admin-error">{error}</p>}
    </>
  );
}

export function AdminOnly({ session, children }) {
  const { t } = useI18n();
  if (session?.role !== 'ADMIN') {
    return (
      <section className="card narrow" data-testid="admin-only">
        <h1>{t('secAdmin')}</h1>
        <p>{t('adminOnly')}</p>
        <p><a href="#/login">{t('navLogin')}</a></p>
      </section>
    );
  }
  return children;
}

/** Labelled form field with its validation message (aria-describedby links the two). */
export function Field({ id, label, error, children, hint }) {
  const { t } = useI18n();
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined;
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {React.cloneElement(children, { id, 'aria-invalid': !!error, 'aria-describedby': describedBy })}
      {error ? <span className="field-error" id={`${id}-error`} data-testid={`${id}-error`}>{t(error)}</span>
        : hint && <span className="hint" id={`${id}-hint`}>{hint}</span>}
    </div>
  );
}

export const when = v => (v ? new Date(v).toLocaleString() : '');
