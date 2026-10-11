import React, { useState } from 'react';
import { useI18n } from '../i18n';
import { HELP } from '../help';

/** Help center: one topic list per language, searchable, each topic an expandable section. */
export default function Help() {
  const { t, lang } = useI18n();
  const [q, setQ] = useState('');
  const query = q.trim().toLowerCase();
  const topics = HELP.map(h => ({ id: h.id, ...(h[lang] || h.en) }))
    .filter(h => !query || h.title.toLowerCase().includes(query) || h.items.some(i => i.toLowerCase().includes(query)));

  return (
    <section className="help" data-testid="help-page">
      <header className="page-head">
        <div>
          <h1 data-testid="help-title">{t('helpTitle')}</h1>
          <p className="muted">{t('helpLead')}</p>
        </div>
        <input type="search" value={q} onChange={e => setQ(e.target.value)} placeholder={t('helpSearch')}
               aria-label={t('helpSearch')} data-testid="help-search" />
      </header>
      <div className="help-layout">
        <nav aria-label={t('topics')} className="help-toc">
          <ul>{topics.map(h => <li key={h.id}><a href={`#/help/${h.id}`}>{h.title}</a></li>)}</ul>
        </nav>
        <div>
          {topics.length === 0 && <p className="card" data-testid="help-empty">{t('helpNone')}</p>}
          {topics.map(h => (
            <details key={h.id} id={`help-${h.id}`} className="help-topic" data-testid="help-topic" data-topic={h.id}
                     open={!!query || window.location.hash === `#/help/${h.id}`}>
              <summary>{h.title}</summary>
              <ul>{h.items.map((i, n) => <li key={n}>{i}</li>)}</ul>
            </details>
          ))}
        </div>
      </div>
    </section>
  );
}
