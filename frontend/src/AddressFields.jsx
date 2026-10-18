import React from 'react';
import { US_STATES } from './usStates';

export const EMPTY_ADDRESS = { fullName: '', line1: '', line2: '', city: '', state: '', zip: '', phone: '' };
const REQUIRED = ['fullName', 'line1', 'city', 'state', 'zip'];

/**
 * Same rules as the backend ShippingAddress: required fields, US state, ZIP 12345 or 12345-6789, phone 7-20 of
 * digits + ( ) - . space. Returns {field: message key}.
 */
export function validateAddress(a) {
  const e = {};
  REQUIRED.forEach(f => { if (!(a[f] ?? '').trim()) e[f] = 'addrMissing'; });
  if (!e.zip && !/^\d{5}(-\d{4})?$/.test(a.zip.trim())) e.zip = 'vZip';
  if ((a.phone ?? '').trim() && !/^[0-9+().\- ]{7,20}$/.test(a.phone.trim())) e.phone = 'vPhone';
  if ((a.fullName ?? '').length > 80) e.fullName = 'vTooLong';
  if ((a.line1 ?? '').length > 100) e.line1 = 'vTooLong';
  if ((a.line2 ?? '').length > 100) e.line2 = 'vTooLong';
  if ((a.city ?? '').length > 60) e.city = 'vTooLong';
  return e;
}

/** Trimmed copy for the API: blank optional fields as null. */
export const addressBody = a => ({ ...a, fullName: a.fullName.trim(), line1: a.line1.trim(), line2: a.line2.trim() || null,
  city: a.city.trim(), zip: a.zip.trim(), phone: a.phone.trim() || null });

export const nullsToEmpty = a => ({ ...EMPTY_ADDRESS, ...Object.fromEntries(Object.entries(a || {}).map(([k, v]) => [k, v ?? ''])) });

/** The delivery address fields (checkout and address book). ids/test ids: `${prefix}-fullName` ...; errors are ready-to-show texts. */
export default function AddressFields({ prefix, value, onChange, errors = {}, t }) {
  const set = name => e => onChange(name, e.target.value);
  const message = name => errors[name] && (
    <span className="field-error" id={`${prefix}-${name}-error`} data-testid={`${prefix}-${name}-error`}>
      {errors[name]}
    </span>
  );
  const field = (name, type = 'text', autoComplete = undefined) => (
    <div className="field">
      <label htmlFor={`${prefix}-${name}`}>{t(`addr${name[0].toUpperCase()}${name.slice(1)}`)}</label>
      <input id={`${prefix}-${name}`} data-testid={`${prefix}-${name}`} type={type} value={value[name]} onChange={set(name)}
             autoComplete={autoComplete} aria-invalid={!!errors[name]}
             aria-describedby={errors[name] ? `${prefix}-${name}-error` : undefined} />
      {message(name)}
    </div>
  );
  return (
    <>
      {field('fullName', 'text', 'name')}
      {field('line1', 'text', 'address-line1')}
      {field('line2', 'text', 'address-line2')}
      {field('city', 'text', 'address-level2')}
      <div className="field">
        <label htmlFor={`${prefix}-state`}>{t('addrState')}</label>
        <select id={`${prefix}-state`} data-testid={`${prefix}-state`} value={value.state} onChange={set('state')}
                autoComplete="address-level1" aria-invalid={!!errors.state}
                aria-describedby={errors.state ? `${prefix}-state-error` : undefined}>
          <option value="">{t('addrChooseState')}</option>
          {US_STATES.map(([code, name]) => <option key={code} value={code}>{code} - {name}</option>)}
        </select>
        {message('state')}
      </div>
      {field('zip', 'text', 'postal-code')}
      {field('phone', 'tel', 'tel')}
    </>
  );
}
