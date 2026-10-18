import React from 'react';
import { isoDay, monthGrid } from './DateInput';

/*
 * Small SVG charts for the dashboard. No chart library: each is a few shapes. Every chart has a text twin
 * (legend with numbers, <title> per mark, an sr-only table) so the numbers never depend on colour alone.
 */

export const STATUS_COLORS = {
  PLACED: '#64748b', PAID: '#2563eb', SHIPPED: '#d97706', DELIVERED: '#16a34a',
  RETURN_REQUESTED: '#db2777', RETURNED: '#7c3aed', CANCELLED: '#dc2626',
};

/** Orders by status as a ring; the legend beside it carries the counts. */
export function Donut({ parts, label, centre, testId }) {
  const total = parts.reduce((s, p) => s + p.value, 0);
  const r = 42;
  const c = 2 * Math.PI * r;
  let offset = 0;
  return (
    <figure className="donut" data-testid={testId}>
      <svg viewBox="0 0 120 120" role="img" aria-label={label}>
        <circle cx="60" cy="60" r={r} className="donut-track" />
        {total > 0 && parts.filter(p => p.value > 0).map(p => {
          const len = (p.value / total) * c;
          const seg = (
            <circle key={p.key} cx="60" cy="60" r={r} fill="none" stroke={p.color} strokeWidth="16"
                    strokeDasharray={`${len} ${c - len}`} strokeDashoffset={-offset} transform="rotate(-90 60 60)">
              <title>{`${p.label}: ${p.value}`}</title>
            </circle>
          );
          offset += len;
          return seg;
        })}
        <text x="60" y="58" textAnchor="middle" className="donut-total">{total}</text>
        <text x="60" y="74" textAnchor="middle" className="donut-caption">{centre}</text>
      </svg>
      <figcaption>
        <ul className="legend">
          {parts.map(p => (
            <li key={p.key} data-testid={`${testId}-${p.key}`}>
              <span className="swatch" style={{ background: p.color }} aria-hidden="true" />
              <span className="legend-label">{p.label}</span><strong>{p.value}</strong>
            </li>
          ))}
        </ul>
      </figcaption>
    </figure>
  );
}

/** One bar per day; today is the last bar. */
export function DayBars({ days, label, format, testId }) {
  const max = Math.max(...days.map(d => d.value), 1);
  const w = 100 / days.length;
  return (
    <figure className="day-bars" data-testid={testId}>
      <svg viewBox="0 0 100 50" preserveAspectRatio="none" role="img" aria-label={label}>
        <defs>
          <linearGradient id="barFill" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0" stopColor="#f59e0b" /><stop offset="1" stopColor="#b45309" />
          </linearGradient>
        </defs>
        {days.map((d, i) => {
          const h = (d.value / max) * 46;
          return (
            <rect key={d.day} x={i * w + w * 0.15} y={48 - Math.max(h, d.value > 0 ? 1.5 : 0.6)} width={w * 0.7}
                  height={Math.max(h, d.value > 0 ? 1.5 : 0.6)} rx="0.8" className={d.value > 0 ? 'bar-fill' : 'bar-zero'}>
              <title>{`${d.day}: ${format(d.value)}`}</title>
            </rect>
          );
        })}
      </svg>
      <div className="bar-axis" aria-hidden="true"><span>{days[0]?.day.slice(5)}</span><span>{days[days.length - 1]?.day.slice(5)}</span></div>
      <table className="sr-only">
        <caption>{label}</caption>
        <tbody>{days.map(d => <tr key={d.day}><th scope="row">{d.day}</th><td>{format(d.value)}</td></tr>)}</tbody>
      </table>
    </figure>
  );
}

/** This month as a calendar: the darker the day, the more orders that day. */
export function MonthHeat({ counts, lang, label, legendFew, legendMany, testId }) {
  const now = new Date();
  const days = monthGrid(now.getFullYear(), now.getMonth());
  const max = Math.max(...Object.values(counts), 1);
  const dayName = new Intl.DateTimeFormat(lang, { weekday: 'narrow' });
  const full = new Intl.DateTimeFormat(lang, { month: 'long', day: 'numeric' });
  const level = n => (n === 0 ? 0 : Math.min(4, Math.ceil((n / max) * 4)));
  const weeks = [0, 1, 2, 3, 4, 5].filter(w => days.slice(w * 7, w * 7 + 7).some(d => d.getMonth() === now.getMonth()));
  return (
    <figure className="heat" data-testid={testId}>
      <table className="heat-grid">
        <caption className="sr-only">{label}</caption>
        <thead><tr>{days.slice(0, 7).map(d => <th key={d.getDay()} scope="col">{dayName.format(d)}</th>)}</tr></thead>
        <tbody>
          {weeks.map(w => (
            <tr key={w}>
              {days.slice(w * 7, w * 7 + 7).map(d => {
                const iso = isoDay(d);
                if (d.getMonth() !== now.getMonth()) return <td key={iso} className="heat-out" />;
                const n = counts[iso] || 0;
                return (
                  <td key={iso} className={`heat-day l${level(n)}${iso === isoDay(now) ? ' today' : ''}`} data-testid={`heat-${iso}`}
                      title={`${full.format(d)}: ${n}`}>
                    <span className="heat-num">{d.getDate()}</span>
                    <span className="sr-only">{`: ${n}`}</span>
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
      <figcaption className="heat-legend" aria-hidden="true">
        {legendFew}<span className="heat-day l0" /><span className="heat-day l1" /><span className="heat-day l2" />
        <span className="heat-day l3" /><span className="heat-day l4" />{legendMany}
      </figcaption>
    </figure>
  );
}
