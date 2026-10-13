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
        <>
          <p className="side-head" id="sec-admin">{t('secAdmin')}</p>
          <ul aria-labelledby="sec-admin" data-testid="admin-menu">
            <Item route={route} href="#/admin" testId="nav-admin-dashboard" onNavigate={onNavigate}>{t('navDashboard')}</Item>
            <Item route={route} href="#/admin/products" testId="nav-admin-products" onNavigate={onNavigate}>{t('navProducts')}</Item>
            <Item route={route} href="#/admin/coupons" testId="nav-admin-coupons" onNavigate={onNavigate}>{t('navCoupons')}</Item>
            <Item route={route} href="#/admin/orders" testId="nav-admin-orders" onNavigate={onNavigate}>{t('navAllOrders')}</Item>
            <Item route={route} href="#/admin/returns" testId="nav-admin-returns" onNavigate={onNavigate}>{t('navReturns')}</Item>
            <Item route={route} href="#/admin/reviews" testId="nav-admin-reviews" onNavigate={onNavigate}>{t('navReviews')}</Item>
            <Item route={route} href="#/admin/notifications" testId="nav-admin-notifications" onNavigate={onNavigate}>{t('navNotifications')}</Item>
          </ul>
        </>
      )}
      <p className="side-head" id="sec-support">{t('secSupport')}</p>
      <ul aria-labelledby="sec-support">
        <Item route={route} href="#/help" testId="side-help" onNavigate={onNavigate}>{t('navHelp')}</Item>
      </ul>
    </nav>
  );
}
