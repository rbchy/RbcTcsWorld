import React, { useEffect, useRef, useState } from 'react';
import { Icon } from './icons';
import { useI18n } from './i18n';

const pad = n => String(n).padStart(2, '0');
export const isoDay = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const parse = s => {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(s || '');
  return m ? new Date(+m[1], +m[2] - 1, +m[3]) : null;
};
const addDays = (d, n) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);

/** The 42 days (6 weeks) shown for a month, starting on Sunday. */
export function monthGrid(year, month) {
  const first = new Date(year, month, 1);
  const start = addDays(first, -first.getDay());
  return Array.from({ length: 42 }, (_, i) => addDays(start, i));
}

/**
 * A date field with a calendar. The typed field stays a real <input type="date"> (or datetime-local), so keyboard
 * users, screen readers, autofill and the tests can still type a date; the calendar button opens a month you can
 * click or move through with the arrow keys (PageUp/PageDown = month, Home/End = week, Enter = pick, Esc = close).
 * Days outside min..max cannot be picked. onChange gets an event-like object, like a normal input.
 */
export default function DateInput({ value = '', onChange, min, max, type = 'date', defaultTime = '00:00', id, ...input }) {
  const { t, lang } = useI18n();
  const [open, setOpen] = useState(false);
  const [focus, setFocus] = useState(() => parse(value) || new Date());
  const wrap = useRef(null);
  const grid = useRef(null);
  const testId = input['data-testid'] || id || 'date';
  const minD = parse(min);
  const maxD = parse(max);
  const outside = d => (minD && d < minD) || (maxD && d > maxD);

  useEffect(() => {
    if (!open) return undefined;
    const away = e => { if (wrap.current && !wrap.current.contains(e.target)) setOpen(false); };
    document.addEventListener('mousedown', away);
    return () => document.removeEventListener('mousedown', away);
  }, [open]);

  useEffect(() => {                       // keep keyboard focus on the highlighted day while the calendar is open
    if (open) grid.current?.querySelector('[data-focus="true"]')?.focus();
  }, [open, focus]);

  const toggle = () => {
    if (!open) setFocus(parse(value) || new Date());
    setOpen(!open);
  };
  const emit = v => onChange?.({ target: { value: v } });
  const pick = d => {
    if (outside(d)) return;
    const time = type === 'datetime-local' ? `T${(value.split('T')[1] || defaultTime).slice(0, 5)}` : '';
    emit(isoDay(d) + time);
    setOpen(false);
  };
  const onKey = e => {
    const moves = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 };
    let next = null;
    if (e.key in moves) next = addDays(focus, (document.documentElement.dir === 'rtl' && Math.abs(moves[e.key]) === 1 ? -1 : 1) * moves[e.key]);
    else if (e.key === 'PageUp') next = new Date(focus.getFullYear(), focus.getMonth() - 1, Math.min(focus.getDate(), 28));
    else if (e.key === 'PageDown') next = new Date(focus.getFullYear(), focus.getMonth() + 1, Math.min(focus.getDate(), 28));
    else if (e.key === 'Home') next = addDays(focus, -focus.getDay());
    else if (e.key === 'End') next = addDays(focus, 6 - focus.getDay());
    else if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); setOpen(false); wrap.current?.querySelector('.cal-btn')?.focus(); return; }
    if (next) { e.preventDefault(); setFocus(next); }
  };

  const month = new Intl.DateTimeFormat(lang, { month: 'long', year: 'numeric' }).format(focus);
  const dayName = new Intl.DateTimeFormat(lang, { weekday: 'short' });
  const fullDate = new Intl.DateTimeFormat(lang, { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });
  const days = monthGrid(focus.getFullYear(), focus.getMonth());
  const selected = value ? value.slice(0, 10) : '';
  const today = isoDay(new Date());
  const shift = n => setFocus(new Date(focus.getFullYear(), focus.getMonth() + n, 1));

  return (
    <span className="date-input" ref={wrap}>
      <input id={id} type={type} value={value} min={min} max={max} onChange={onChange} {...input} />
      <button type="button" className="cal-btn" aria-haspopup="dialog" aria-expanded={open} onClick={toggle}
              aria-label={t('calOpen')} title={t('calOpen')} data-testid={`${testId}-cal`}>
        <Icon name="calendar" />
      </button>
      {open && (
        <div className="cal-pop" role="dialog" aria-label={t('calChoose')} data-testid={`${testId}-calendar`} onKeyDown={onKey}>
          <div className="cal-head">
            <button type="button" className="cal-nav" onClick={() => shift(-1)} aria-label={t('calPrevMonth')} data-testid="cal-prev-month">
              <Icon name="chevronLeft" />
            </button>
            <p className="cal-month" aria-live="polite" data-testid="cal-month">{month}</p>
            <button type="button" className="cal-nav" onClick={() => shift(1)} aria-label={t('calNextMonth')} data-testid="cal-next-month">
              <Icon name="chevronRight" />
            </button>
          </div>
          <table className="cal-grid" ref={grid}>
            <thead>
              <tr>{days.slice(0, 7).map(d => <th key={d.getDay()} scope="col" abbr={d.toLocaleDateString(lang, { weekday: 'long' })}>{dayName.format(d)}</th>)}</tr>
            </thead>
            <tbody>
              {[0, 1, 2, 3, 4, 5].map(w => (
                <tr key={w}>
                  {days.slice(w * 7, w * 7 + 7).map(d => {
                    const iso = isoDay(d);
                    const cls = ['cal-day', d.getMonth() !== focus.getMonth() && 'other', iso === today && 'today', iso === selected && 'picked']
                      .filter(Boolean).join(' ');
                    const isFocus = iso === isoDay(focus);
                    return (
                      <td key={iso}>
                        <button type="button" className={cls} tabIndex={isFocus ? 0 : -1} data-focus={isFocus}
                                aria-pressed={iso === selected} aria-label={fullDate.format(d)} disabled={!!outside(d)}
                                data-testid={`cal-day-${iso}`} onClick={() => pick(d)}>
                          {d.getDate()}
                        </button>
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
          <div className="cal-foot">
            <button type="button" className="ghost" onClick={() => pick(new Date())} disabled={!!outside(parse(today))} data-testid="cal-today">{t('calToday')}</button>
            <button type="button" className="ghost" onClick={() => { emit(''); setOpen(false); }} data-testid="cal-clear">{t('calClear')}</button>
          </div>
        </div>
      )}
    </span>
  );
}
