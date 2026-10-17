import React, { useEffect, useState } from 'react';
import { go } from './session';
import { logout } from './api';
import { LANGUAGES, useI18n } from './i18n';

/** Install button for the desktop version (PWA): Chrome/Edge fire beforeinstallprompt when the app can be installed. */
function useInstallPrompt() {
  const [prompt, setPrompt] = useState(null);
  useEffect(() => {
    const onPrompt = e => { e.preventDefault(); setPrompt(e); };
    const onInstalled = () => setPrompt(null);
    window.addEventListener('beforeinstallprompt', onPrompt);
    window.addEventListener('appinstalled', onInstalled);
    return () => {
      window.removeEventListener('beforeinstallprompt', onPrompt);
      window.removeEventListener('appinstalled', onInstalled);
    };
  }, []);
  return prompt && (() => { prompt.prompt(); prompt.userChoice.finally(() => setPrompt(null)); });
}

/** Small line icons (decorative: the button text is the accessible name). */
const ICONS = {
  logout: 'M10 4H5a1 1 0 0 0-1 1v14a1 1 0 0 0 1 1h5M15 8l4 4-4 4M19 12H9',
  exit: 'M12 3v8M7.1 6.4a7 7 0 1 0 9.8 0',
};
export function Icon({ name }) {
  return (
    <svg className="icon" viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" focusable="false">
      <path d={ICONS[name]} fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

/** Log out: the server revokes the refresh token, this browser forgets the session, back to the catalog. */
export function signOut() {
  logout();
  go('/');
}

/**
 * Exit: log out (when signed in), show the goodbye page and ask the browser to close the window.
 * Browsers only allow window.close() for windows a script or the installed app opened; otherwise the goodbye
 * page stays and explains how to close it. Exit never stops the server: that is the Stop launcher's job.
 */
export function exitShop() {
  logout();
  go('/goodbye');
  setTimeout(() => window.close(), 150);
}

export function Topbar({ session, onMenu, menuOpen }) {
  const { t, lang, setLanguage } = useI18n();
  const install = useInstallPrompt();
  return (
    <header className="topbar">
      <button className="menu-btn" aria-expanded={menuOpen} aria-controls="sidebar" onClick={onMenu} data-testid="menu-toggle">
        <span aria-hidden="true" className="burger" />{menuOpen ? t('closeMenu') : t('menu')}
      </button>
      <a href="#/" className="brand" data-testid="nav-home">
        <span className="brand-mark" aria-hidden="true">R</span>
        <span className="brand-text">RbcTcsWorld<small>{t('tagline')}</small></span>
      </a>
      <div className="top-actions">
        <label className="lang">
          <span className="sr-only">{t('language')}</span>
          <select value={lang} onChange={e => setLanguage(e.target.value)} data-testid="lang-select">
            {LANGUAGES.map(l => <option key={l.code} value={l.code} lang={l.code}>{l.label}</option>)}
          </select>
        </label>
        {install && <button className="ghost on-dark" onClick={install} data-testid="install-app">{t('installApp')}</button>}
        <a href="#/help" className="top-link" data-testid="nav-help">{t('navHelp')}</a>
        {session ? (
          <div className="user-chip">
            <span className="sr-only">{t('signedInAs')}</span>
            <span className="who" data-testid="nav-user">{session.email}</span>
            {session.role === 'ADMIN' && <span className="role-badge" data-testid="nav-role">{t('adminBadge')}</span>}
            <button className="top-btn" data-testid="nav-logout" onClick={signOut}>
              <Icon name="logout" /><span className="btn-label">{t('navLogout')}</span>
            </button>
          </div>
        ) : (
          <>
            <a href="#/login" className="top-link" data-testid="nav-login">{t('navLogin')}</a>
            <a href="#/register" className="top-cta" data-testid="nav-register">{t('navRegister')}</a>
          </>
        )}
        <button className="top-btn exit" data-testid="nav-exit" onClick={exitShop} title={t('exitHint')}>
          <Icon name="exit" /><span className="btn-label">{t('navExit')}</span>
        </button>
      </div>
    </header>
  );
}

function Item({ route, href, testId, children, onNavigate }) {
  const path = href.slice(1);
  const active = route === path || (path !== '/' && path !== '/admin' && route.startsWith(`${path}/`));
  return (
    <li>
      <a href={href} data-testid={testId} aria-current={active ? 'page' : undefined} onClick={onNavigate}>{children}</a>
    </li>
  );
}

/** Admin menu: one sub-menu per area of the store (each can be folded away). */
export const ADMIN_MENU = [
  { key: 'overview', title: 'grpOverview', items: [
    ['#/admin', 'nav-admin-dashboard', 'navDashboard'],
    ['#/admin/reports', 'nav-admin-reports', 'navReports']] },
  { key: 'catalog', title: 'grpCatalog', items: [
    ['#/admin/products', 'nav-admin-products', 'navProducts'],
    ['#/admin/coupons', 'nav-admin-coupons', 'navCoupons'],
    ['#/admin/warehouses', 'nav-admin-warehouses', 'navWarehouses']] },
  { key: 'sales', title: 'grpSales', items: [
    ['#/admin/orders', 'nav-admin-orders', 'navAllOrders'],
    ['#/admin/payments', 'nav-admin-payments', 'navPayments'],
    ['#/admin/returns', 'nav-admin-returns', 'navReturns']] },
  { key: 'customers', title: 'grpCustomers', items: [
    ['#/admin/customers', 'nav-admin-customers', 'navCustomers'],
    ['#/admin/reviews', 'nav-admin-reviews', 'navReviews'],
    ['#/admin/notifications', 'nav-admin-notifications', 'navNotifications']] },
  { key: 'control', title: 'grpControl', items: [
    ['#/admin/audit', 'nav-admin-audit', 'navAudit']] },
];

export function Sidebar({ route, session, open, onNavigate }) {
  const { t } = useI18n();
  const isAdmin = session?.role === 'ADMIN';
  return (
    <nav id="sidebar" className={`sidebar${open ? ' open' : ''}`} aria-label={t('menu')}>
      {open && <button className="ghost side-close" onClick={onNavigate} data-testid="menu-close">{t('closeMenu')}</button>}
      <p className="side-head" id="sec-shop">{t('secShop')}</p>
      <ul aria-labelledby="sec-shop">
        <Item route={route} href="#/" testId="nav-catalog" onNavigate={onNavigate}>{t('navCatalog')}</Item>
        <Item route={route} href="#/wishlist" testId="nav-wishlist" onNavigate={onNavigate}>{t('navWishlist')}</Item>
        <Item route={route} href="#/cart" testId="nav-cart" onNavigate={onNavigate}>{t('navCart')}</Item>
        <Item route={route} href="#/orders" testId="nav-orders" onNavigate={onNavigate}>{t('navOrders')}</Item>
      </ul>
      <p className="side-head" id="sec-account">{t('secAccount')}</p>
      <ul aria-labelledby="sec-account">
        {session ? (
          <li>
            <button className="side-action" data-testid="side-logout" onClick={() => { onNavigate(); signOut(); }}>
              <Icon name="logout" />{t('navLogout')}
            </button>
          </li>
        ) : (
          <>
            <Item route={route} href="#/login" testId="side-login" onNavigate={onNavigate}>{t('navLogin')}</Item>
            <Item route={route} href="#/register" testId="side-register" onNavigate={onNavigate}>{t('navRegister')}</Item>
          </>
        )}
        <li>
          <button className="side-action" data-testid="side-exit" onClick={() => { onNavigate(); exitShop(); }}>
            <Icon name="exit" />{t('navExit')}
          </button>
        </li>
      </ul>
      {isAdmin && (
        <div className="admin-menu" data-testid="admin-menu">
          <p className="side-head" id="sec-admin">{t('secAdmin')}</p>
          {ADMIN_MENU.map(g => (
            <details key={g.key} className="side-group" open data-testid={`admin-group-${g.key}`}>
              <summary>{t(g.title)}</summary>
              <ul aria-label={t(g.title)}>
                {g.items.map(([href, testId, label]) => (
                  <Item key={href} route={route} href={href} testId={testId} onNavigate={onNavigate}>{t(label)}</Item>
                ))}
              </ul>
            </details>
          ))}
        </div>
      )}
      <p className="side-head" id="sec-support">{t('secSupport')}</p>
      <ul aria-labelledby="sec-support">
        <Item route={route} href="#/help" testId="side-help" onNavigate={onNavigate}>{t('navHelp')}</Item>
      </ul>
    </nav>
  );
}
