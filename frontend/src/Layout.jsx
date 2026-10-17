import React, { useEffect, useState } from 'react';
import { go } from './session';
import api, { logout } from './api';
import { LANGUAGES, useI18n } from './i18n';
import { Icon } from './icons';

export { Icon };

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

/**
 * Items in the cart, for the badge on the cart icon. Read once when a customer signs in, then kept up to date by
 * the 'cart-count' event that api.js sends whenever any cart answer comes back (add, change, remove, order).
 */
function useCartCount(session) {
  const [count, setCount] = useState(null);
  const customer = session?.role === 'CUSTOMER';
  useEffect(() => {
    if (!customer) { setCount(null); return undefined; }
    let live = true;
    const read = () => api.get('/cart')
      .then(r => { if (live && Number.isFinite(r.data?.totalQuantity)) setCount(r.data.totalQuantity); })
      .catch(() => {});
    read();
    const onCount = e => setCount(e.detail);
    window.addEventListener('cart-count', onCount);
    window.addEventListener('cart-refresh', read);
    return () => {
      live = false;
      window.removeEventListener('cart-count', onCount);
      window.removeEventListener('cart-refresh', read);
    };
  }, [customer, session?.email]);
  return customer ? count : null;
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
  const cartCount = useCartCount(session);
  const cartName = cartCount ? t('cartWithCount', { n: cartCount }) : t('navCart');
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
          <Icon name="globe" className="lang-icon" />
          <select value={lang} onChange={e => setLanguage(e.target.value)} data-testid="lang-select">
            {LANGUAGES.map(l => <option key={l.code} value={l.code} lang={l.code}>{l.label}</option>)}
          </select>
        </label>
        {install && <button className="ghost on-dark" onClick={install} data-testid="install-app">{t('installApp')}</button>}
        <a href="#/help" className="top-icon" data-testid="nav-help">
          <Icon name="help" /><span className="btn-label">{t('navHelp')}</span>
        </a>
        {session?.role !== 'ADMIN' && (
          <>
            <a href="#/wishlist" className="top-icon hide-phone" data-testid="top-wishlist">
              <Icon name="heart" /><span className="btn-label">{t('navWishlist')}</span>
            </a>
            <a href="#/cart" className="top-icon cart-link" data-testid="top-cart" aria-label={cartName}>
              <Icon name="cart" /><span className="btn-label" aria-hidden="true">{t('navCart')}</span>
              {cartCount > 0 && <span className="cart-badge" data-testid="top-cart-count" aria-hidden="true">{cartCount > 99 ? '99+' : cartCount}</span>}
            </a>
          </>
        )}
        {session ? (
          <div className="user-chip">
            <span className="sr-only">{t('signedInAs')}</span>
            <span className="avatar" aria-hidden="true">{(session.email || '?').charAt(0).toUpperCase()}</span>
            <span className="who" data-testid="nav-user">{session.email}</span>
            {session.role === 'ADMIN' && <span className="role-badge" data-testid="nav-role">{t('adminBadge')}</span>}
            {session.guest && <span className="role-badge guest" data-testid="nav-guest">{t('guestBadge')}</span>}
            <button className="top-btn" data-testid="nav-logout" onClick={signOut}>
              <Icon name="logout" /><span className="btn-label">{t('navLogout')}</span>
            </button>
          </div>
        ) : (
          <>
            <a href="#/login" className="top-link" data-testid="nav-login"><Icon name="login" />{t('navLogin')}</a>
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

function Item({ route, href, testId, icon, children, onNavigate }) {
  const path = href.slice(1);
  const active = route === path || (path !== '/' && path !== '/admin' && route.startsWith(`${path}/`));
  return (
    <li>
      <a href={href} data-testid={testId} aria-current={active ? 'page' : undefined} onClick={onNavigate}>
        <span className="tile" aria-hidden="true"><Icon name={icon} size={16} /></span>{children}
      </a>
    </li>
  );
}

/** Admin menu: one sub-menu per area of the store (each can be folded away), each area with its own colour. */
export const ADMIN_MENU = [
  { key: 'overview', title: 'grpOverview', items: [
    ['#/admin', 'nav-admin-dashboard', 'navDashboard', 'grid'],
    ['#/admin/reports', 'nav-admin-reports', 'navReports', 'chart']] },
  { key: 'catalog', title: 'grpCatalog', items: [
    ['#/admin/products', 'nav-admin-products', 'navProducts', 'tag'],
    ['#/admin/coupons', 'nav-admin-coupons', 'navCoupons', 'ticket'],
    ['#/admin/warehouses', 'nav-admin-warehouses', 'navWarehouses', 'warehouse']] },
  { key: 'sales', title: 'grpSales', items: [
    ['#/admin/orders', 'nav-admin-orders', 'navAllOrders', 'receipt'],
    ['#/admin/payments', 'nav-admin-payments', 'navPayments', 'card'],
    ['#/admin/returns', 'nav-admin-returns', 'navReturns', 'returnArrow']] },
  { key: 'customers', title: 'grpCustomers', items: [
    ['#/admin/customers', 'nav-admin-customers', 'navCustomers', 'users'],
    ['#/admin/reviews', 'nav-admin-reviews', 'navReviews', 'star'],
    ['#/admin/notifications', 'nav-admin-notifications', 'navNotifications', 'bell']] },
  { key: 'control', title: 'grpControl', items: [
    ['#/admin/audit', 'nav-admin-audit', 'navAudit', 'shield']] },
];

export function Sidebar({ route, session, open, onNavigate }) {
  const { t } = useI18n();
  const isAdmin = session?.role === 'ADMIN';
  return (
    <nav id="sidebar" className={`sidebar${open ? ' open' : ''}`} aria-label={t('menu')}>
      {open && <button className="ghost side-close" onClick={onNavigate} data-testid="menu-close">{t('closeMenu')}</button>}
      <div className="side-sec c-shop">
        <p className="side-head" id="sec-shop">{t('secShop')}</p>
        <ul aria-labelledby="sec-shop">
          <Item route={route} href="#/" testId="nav-catalog" icon="bag" onNavigate={onNavigate}>{t('navCatalog')}</Item>
          <Item route={route} href="#/wishlist" testId="nav-wishlist" icon="heart" onNavigate={onNavigate}>{t('navWishlist')}</Item>
          <Item route={route} href="#/cart" testId="nav-cart" icon="cart" onNavigate={onNavigate}>{t('navCart')}</Item>
          <Item route={route} href="#/orders" testId="nav-orders" icon="box" onNavigate={onNavigate}>{t('navOrders')}</Item>
          <Item route={route} href="#/addresses" testId="nav-addresses" icon="pin" onNavigate={onNavigate}>{t('navAddresses')}</Item>
        </ul>
      </div>
      <div className="side-sec c-account">
        <p className="side-head" id="sec-account">{t('secAccount')}</p>
        <ul aria-labelledby="sec-account">
          {session ? (
            <li>
              <button className="side-action" data-testid="side-logout" onClick={() => { onNavigate(); signOut(); }}>
                <span className="tile" aria-hidden="true"><Icon name="logout" size={16} /></span>{t('navLogout')}
              </button>
            </li>
          ) : (
            <>
              <Item route={route} href="#/login" testId="side-login" icon="login" onNavigate={onNavigate}>{t('navLogin')}</Item>
              <Item route={route} href="#/register" testId="side-register" icon="userPlus" onNavigate={onNavigate}>{t('navRegister')}</Item>
            </>
          )}
          <li>
            <button className="side-action" data-testid="side-exit" onClick={() => { onNavigate(); exitShop(); }}>
              <span className="tile" aria-hidden="true"><Icon name="exit" size={16} /></span>{t('navExit')}
            </button>
          </li>
        </ul>
      </div>
      {isAdmin && (
        <div className="admin-menu" data-testid="admin-menu">
          <p className="side-head" id="sec-admin">{t('secAdmin')}</p>
          {ADMIN_MENU.map(g => (
            <details key={g.key} className={`side-group c-${g.key}`} open data-testid={`admin-group-${g.key}`}>
              <summary><span className="dot" aria-hidden="true" />{t(g.title)}</summary>
              <ul aria-label={t(g.title)}>
                {g.items.map(([href, testId, label, icon]) => (
                  <Item key={href} route={route} href={href} testId={testId} icon={icon} onNavigate={onNavigate}>{t(label)}</Item>
                ))}
              </ul>
            </details>
          ))}
        </div>
      )}
      <div className="side-sec c-support">
        <p className="side-head" id="sec-support">{t('secSupport')}</p>
        <ul aria-labelledby="sec-support">
          <Item route={route} href="#/help" testId="side-help" icon="help" onNavigate={onNavigate}>{t('navHelp')}</Item>
        </ul>
      </div>
    </nav>
  );
}

/** The bottom of every page: where to go next, how the shop works, and a way back to the top. */
export function Footer({ session }) {
  const { t } = useI18n();
  const year = new Date().getFullYear();
  const toTop = () => {
    const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    window.scrollTo({ top: 0, behavior: reduce ? 'auto' : 'smooth' });
    document.getElementById('main')?.focus({ preventScroll: true });
  };
  return (
    <footer className="site-footer" data-testid="site-footer">
      <div className="foot-stripe" aria-hidden="true" />
      <div className="foot-grid">
        <section className="foot-brand" aria-labelledby="foot-brand">
          <p className="brand foot-logo" id="foot-brand"><span className="brand-mark" aria-hidden="true">R</span>RbcTcsWorld</p>
          <p>{t('tagline')}</p>
          <ul className="foot-promises">
            <li><Icon name="truck" />{t('footFreeShipping')}</li>
            <li><Icon name="returnArrow" />{t('footReturns')}</li>
            <li><Icon name="lock" />{t('footSecure')}</li>
          </ul>
        </section>
        <nav aria-labelledby="foot-shop">
          <h2 id="foot-shop">{t('secShop')}</h2>
          <ul>
            <li><a href="#/" data-testid="foot-catalog">{t('navCatalog')}</a></li>
            <li><a href="#/wishlist">{t('navWishlist')}</a></li>
            <li><a href="#/cart">{t('navCart')}</a></li>
            <li><a href="#/orders">{t('navOrders')}</a></li>
          </ul>
        </nav>
        <nav aria-labelledby="foot-account">
          <h2 id="foot-account">{t('secAccount')}</h2>
          <ul>
            {session ? <li><a href="#/orders">{t('footMyOrders')}</a></li> : (
              <>
                <li><a href="#/login">{t('navLogin')}</a></li>
                <li><a href="#/register">{t('navRegister')}</a></li>
              </>
            )}
            <li><a href="#/forgot-password">{t('footForgot')}</a></li>
            {session?.role === 'ADMIN' && <li><a href="#/admin">{t('navDashboard')}</a></li>}
          </ul>
        </nav>
        <nav aria-labelledby="foot-help">
          <h2 id="foot-help">{t('secSupport')}</h2>
          <ul>
            <li><a href="#/help" data-testid="foot-help">{t('navHelp')}</a></li>
            <li><a href="#/help">{t('footShippingHelp')}</a></li>
            <li><a href="#/help">{t('footReturnsHelp')}</a></li>
          </ul>
        </nav>
      </div>
      <div className="foot-bottom">
        <p>© {year} RbcTcsWorld. {t('footDemo')}</p>
        <p className="pay-methods">
          <span>{t('footWeAccept')}</span>
          <span className="pay-chip"><Icon name="card" size={16} />{t('footCards')}</span>
        </p>
        <button type="button" className="foot-top" onClick={toTop} data-testid="footer-top">
          <Icon name="up" size={16} />{t('footBackToTop')}
        </button>
      </div>
    </footer>
  );
}
