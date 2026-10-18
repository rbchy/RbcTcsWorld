import React, { useEffect, useState } from 'react';
import { money } from './api';
import { useI18n } from './i18n';

/** The API sends deal times in UTC without a "Z"; add it so the browser reads them as UTC. */
const utc = s => new Date(`${s}Z`).getTime();

/** "2d 03:04:05" / "03:04:05"; null once the time is up. */
export function timeLeft(endsAt, now = Date.now()) {
  const ms = utc(endsAt) - now;
  if (!(ms > 0)) return null;
  const s = Math.floor(ms / 1000);
  const d = Math.floor(s / 86400);
  const pad = n => String(n).padStart(2, '0');
  const hms = `${pad(Math.floor((s % 86400) / 3600))}:${pad(Math.floor((s % 3600) / 60))}:${pad(s % 60)}`;
  return d > 0 ? `${d}d ${hms}` : hms;
}

/** Live while the server says so and the end time has not passed on this clock either. */
export const isLive = p => Boolean(p && p.dealLive && p.dealEndsAt && utc(p.dealEndsAt) > Date.now());

export function Countdown({ endsAt, testId = 'deal-countdown' }) {
  const { t } = useI18n();
  const [now, setNow] = useState(Date.now());
  useEffect(() => { const id = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(id); }, []);
  const left = timeLeft(endsAt, now);
  return left
    ? <span className="deal-countdown" data-testid={testId}>{t('dealEndsIn')} <time>{left}</time></span>
    : <span className="deal-countdown over" data-testid={testId}>{t('dealOver')}</span>;
}

/** Price with the deal: sale price, struck-through regular price, percent off. Plain price when no deal is live. */
export function Price({ p, testId = 'product-price' }) {
  const { t } = useI18n();
  if (!isLive(p)) return <strong data-testid={testId}>{money(p.price)}</strong>;
  const off = Math.round((1 - Number(p.dealPrice) / Number(p.price)) * 100);
  return (
    <span className="deal-price">
      <strong data-testid={testId} className="now">{money(p.dealPrice)}</strong>
      <s className="was" data-testid="deal-was" aria-label={t('dealWas')}>{money(p.price)}</s>
      <span className="deal-badge" data-testid="deal-badge">-{off}%</span>
    </span>
  );
}
