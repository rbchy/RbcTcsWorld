import React, { useEffect, useState } from 'react';
import api, { errorText, money } from '../../api';
import { useI18n } from '../../i18n';
import { Notice } from './common';
import Watermark from '../../Watermark';
import DateInput from '../../DateInput';

export const REPORTS = ['sales', 'top-products', 'orders-by-status', 'returns', 'coupons', 'low-stock', 'inventory', 'stock-by-warehouse'];
const SNAPSHOT = ['low-stock', 'inventory', 'stock-by-warehouse'];                        // stock as it is now: no dates
const MONEY = ['subtotal', 'discount', 'shipping', 'tax', 'total', 'revenue', 'amount', 'refunded', 'value', 'orderTotal', 'price'];

const iso = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
function lastDays(n) {
  const to = new Date();
  const from = new Date(to);
  from.setDate(to.getDate() - (n - 1));
  return { from: iso(from), to: iso(to) };
}
const DEFAULTS = () => ({ name: 'sales', ...lastDays(30), groupBy: 'day', limit: '10', threshold: '5' });

/** Same rules as ReportService: from <= to, at most 366 days, limit 1..100, threshold 0..1000. */
export function validateReport(f) {
  const e = {};
  if (!SNAPSHOT.includes(f.name)) {
    if (!f.from) e.from = 'vRequired';
    if (!f.to) e.to = 'vRequired';
    if (f.from && f.to) {
      if (f.from > f.to) e.to = 'vEndBeforeStart';
      else if ((new Date(f.to) - new Date(f.from)) / 86400000 + 1 > 366) e.to = 'vRange366';
    }
  }
  if (f.name === 'top-products' && !(/^\d+$/.test(f.limit) && +f.limit >= 1 && +f.limit <= 100)) e.limit = 'vLimit100';
  if (f.name === 'low-stock' && !(/^\d+$/.test(f.threshold) && +f.threshold <= 1000)) e.threshold = 'vThreshold';
  return e;
}

function params(f) {
  const p = {};
  if (!SNAPSHOT.includes(f.name)) { p.from = f.from; p.to = f.to; }
  if (f.name === 'sales') p.groupBy = f.groupBy;
  if (f.name === 'top-products') p.limit = f.limit;
  if (f.name === 'low-stock') p.threshold = f.threshold;
  return p;
}

/** Sales as bars: one bar per day or month, the height is the money received. */
function SalesBars({ table, t }) {
  const totals = table.rows.map(r => Number(r[7]));
  const max = Math.max(...totals, 1);
  const w = Math.max(6, Math.min(36, Math.floor(640 / Math.max(totals.length, 1)) - 2));
  return (
    <figure className="card chart" data-testid="sales-chart">
      <figcaption className="small muted">{t('rptChartCaption')}</figcaption>
      <svg viewBox={`0 0 ${totals.length * (w + 2)} 120`} role="img" aria-label={t('rptChartCaption')} preserveAspectRatio="none">
        {totals.map((v, i) => (
          <rect key={i} x={i * (w + 2)} y={118 - (v / max) * 110} width={w} height={Math.max((v / max) * 110, v > 0 ? 2 : 0.5)}
                className={v > 0 ? 'bar' : 'bar empty'}>
            <title>{`${table.rows[i][0]}: ${money(v)}`}</title>
          </rect>
        ))}
      </svg>
    </figure>
  );
}

/** Store reports: pick one, choose the period, run it; print it or download it as CSV. */
export default function AdminReports() {
  const { t } = useI18n();
  const [form, setForm] = useState(DEFAULTS);
  const [errors, setErrors] = useState({});
  const [table, setTable] = useState(null);
  const [ran, setRan] = useState(null);                         // the options the shown table was made with
  const [error, setError] = useState('');

  const run = f => {
    const found = validateReport(f);
    setErrors(found);
    if (Object.keys(found).length) return;
    api.get(`/admin/reports/${f.name}`, { params: params(f) })
      .then(r => { setTable(r.data); setRan(f); setError(''); }).catch(e => { setTable(null); setError(errorText(e)); });
  };
  useEffect(() => { run(form); }, []);

  const set = k => e => {
    const next = { ...form, [k]: e.target.value };
    setForm(next);
    if (Object.keys(errors).length) setErrors(validateReport(next));
    if (k === 'name') run(next);
  };

  const download = async () => {
    try {
      const r = await api.get(`/admin/reports/${ran.name}/export`, { params: params(ran), responseType: 'blob' });
      const url = URL.createObjectURL(r.data);
      const a = document.createElement('a');
      a.href = url;
      a.download = `report-${ran.name}.csv`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 10000);
    } catch (err) { setError(errorText(err)); }
  };

  const cell = (col, v) => (v == null ? '' : MONEY.includes(col) ? money(v)
    : col === 'status' || col === 'state' || col === 'reason' ? <span className="badge">{v}</span> : String(v));
  const snapshot = SNAPSHOT.includes(form.name);

  return (
    <section data-testid="admin-reports" className="reports">
      <header className="page-head no-print">
        <h1>{t('navReports')}</h1>
        {table && (
          <div className="row">
            <button type="button" onClick={download} data-testid="report-export">{t('auditExport')}</button>
            <button type="button" className="ghost" onClick={() => window.print()} data-testid="report-print">{t('print')}</button>
          </div>
        )}
      </header>
      <Notice error={error} />

      <form className="card filters no-print" onSubmit={e => { e.preventDefault(); run(form); }} noValidate data-testid="report-form"
            aria-label={t('navReports')}>
        <label className="field">{t('report')}
          <select value={form.name} onChange={set('name')} data-testid="report-name">
            {REPORTS.map(n => <option key={n} value={n}>{t(`rpt_${n}`)}</option>)}
          </select>
        </label>
        {!snapshot && (
          <>
            <FieldInput id="rp-from" label={t('fromDate')} type="date" value={form.from} onChange={set('from')} error={errors.from} t={t} />
            <FieldInput id="rp-to" label={t('toDate')} type="date" value={form.to} onChange={set('to')} error={errors.to} t={t} />
          </>
        )}
        {form.name === 'sales' && (
          <label className="field">{t('groupBy')}
            <select value={form.groupBy} onChange={set('groupBy')} data-testid="report-group">
              <option value="day">{t('perDay')}</option>
              <option value="month">{t('perMonth')}</option>
            </select>
          </label>
        )}
        {form.name === 'top-products' && (
          <FieldInput id="rp-limit" label={t('howMany')} value={form.limit} onChange={set('limit')} error={errors.limit} t={t} inputMode="numeric" />
        )}
        {form.name === 'low-stock' && (
          <FieldInput id="rp-threshold" label={t('stockAtMost')} value={form.threshold} onChange={set('threshold')} error={errors.threshold} t={t} inputMode="numeric" />
        )}
        <div className="filter-actions">
          <button type="submit" data-testid="report-run">{t('showReport')}</button>
          {!snapshot && ['7', '30', '90'].map(n => (
            <button key={n} type="button" className="ghost" data-testid={`report-last-${n}`}
                    onClick={() => { const next = { ...form, ...lastDays(+n) }; setForm(next); run(next); }}>{t('lastNDays', { n })}</button>
          ))}
        </div>
      </form>

      {table && (
        <article className="report-sheet" data-testid="report-sheet" data-report={table.name}>
          <header className="report-head">
            <Watermark tone="light" />
            <p className="report-brand">RbcTcsWorld</p>
            <h2 data-testid="report-title">{t(`rpt_${table.name}`)}</h2>
            <p className="small muted" data-testid="report-period">
              {table.from ? t('rptPeriod', { from: table.from, to: table.to }) : t('rptSnapshot', { at: new Date().toLocaleString() })}
            </p>
          </header>
          {table.name === 'sales' && table.rows.length > 1 && <SalesBars table={table} t={t} />}
          <div className="table-wrap" tabIndex={0} role="region" aria-label={t(`rpt_${table.name}`)}>
            <table data-testid="report-table">
              <thead><tr>{table.columns.map(c => (
                <th key={c} className={MONEY.includes(c) || typeof table.rows[0]?.[table.columns.indexOf(c)] === 'number' ? 'num' : ''}>{t(`col_${c}`)}</th>
              ))}</tr></thead>
              <tbody>
                {table.rows.length === 0 && <tr><td colSpan={table.columns.length} className="muted">{t('noRows')}</td></tr>}
                {table.rows.map((r, i) => (
                  <tr key={i} data-testid="report-row">{r.map((v, j) => (
                    <td key={j} className={typeof v === 'number' ? 'num' : ''}>{cell(table.columns[j], v)}</td>
                  ))}</tr>
                ))}
              </tbody>
              {table.totals && (
                <tfoot><tr data-testid="report-totals">{table.totals.map((v, j) => (
                  <th key={j} className={typeof v === 'number' ? 'num' : ''} scope={j === 0 ? 'row' : undefined}>
                    {j === 0 ? t('col_total') : cell(table.columns[j], v)}</th>
                ))}</tr></tfoot>
              )}
            </table>
          </div>
        </article>
      )}
    </section>
  );
}

function FieldInput({ id, label, error, t, ...input }) {
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {input.type === 'date'
        ? <DateInput id={id} data-testid={id} aria-invalid={!!error} aria-describedby={error ? `${id}-error` : undefined} {...input} />
        : <input id={id} data-testid={id} aria-invalid={!!error} aria-describedby={error ? `${id}-error` : undefined} {...input} />}
      {error && <span className="field-error" id={`${id}-error`} data-testid={`${id}-error`}>{t(error)}</span>}
    </div>
  );
}
