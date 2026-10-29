import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import '@fontsource/plus-jakarta-sans/latin-700.css';
import '@fontsource/plus-jakarta-sans/latin-800.css';
import './style.css';
import { useRoute, useSession } from './session';
import { AnnounceBar, CookieNotice, Footer, Sidebar, Topbar } from './Layout';
import Legal, { POLICIES } from './pages/Legal';
import { installButtonIcons } from './icons';
import { useHead } from './head';
import { useI18n } from './i18n';
import Home from './pages/Home';
import Login from './pages/Login';
import { ForgotPassword, ResetPassword } from './pages/PasswordReset';
import Cart from './pages/Cart';
import Checkout from './pages/Checkout';
import Orders from './pages/Orders';
import OrderDetail from './pages/OrderDetail';
import ProductDetail from './pages/ProductDetail';
import Wishlist from './pages/Wishlist';
import Addresses from './pages/Addresses';
import PaymentMethods from './pages/PaymentMethods';
import Security from './pages/Security';
import Help from './pages/Help';
import Contact from './pages/Contact';
import Goodbye from './pages/Goodbye';
import { AdminOnly } from './pages/admin/common';
import AdminDashboard from './pages/admin/AdminDashboard';
import AdminProducts from './pages/admin/AdminProducts';
import AdminCoupons from './pages/admin/AdminCoupons';
import AdminOrders from './pages/admin/AdminOrders';
import AdminReturns from './pages/admin/AdminReturns';
import AdminReviews from './pages/admin/AdminReviews';
import AdminNotifications from './pages/admin/AdminNotifications';
import AdminAudit from './pages/admin/AdminAudit';
import AdminCustomers from './pages/admin/AdminCustomers';
import AdminPayments from './pages/admin/AdminPayments';
import AdminReports from './pages/admin/AdminReports';
import AdminWarehouses from './pages/admin/AdminWarehouses';
import AdminMessages from './pages/admin/AdminMessages';

const ADMIN = {
  '/admin': AdminDashboard, '/admin/products': AdminProducts, '/admin/coupons': AdminCoupons,
  '/admin/orders': AdminOrders, '/admin/returns': AdminReturns, '/admin/reviews': AdminReviews,
  '/admin/notifications': AdminNotifications, '/admin/audit': AdminAudit,
  '/admin/customers': AdminCustomers, '/admin/payments': AdminPayments, '/admin/reports': AdminReports, '/admin/warehouses': AdminWarehouses,
  '/admin/messages': AdminMessages,
};

/** Public demo only (built with VITE_DEMO=true, see deploy/): says plainly that nothing here is real. */
const DEMO = import.meta.env.VITE_DEMO === 'true';
function DemoRibbon() {
  const { t } = useI18n();
  return <p className="demo-ribbon" role="note" data-testid="demo-ribbon">{t('demoRibbon')}</p>;
}

/** What search engines see for each page (ADR-0019): public pages are indexable with their real path; the product
 *  page sets its own once loaded; everything personal (cart, orders, admin, sign-in) is noindex. */
const PRIVATE_TITLES = { '/cart': 'navCart', '/checkout': 'navCart', '/orders': 'navOrders', '/wishlist': 'navWishlist',
  '/login': 'navLogin', '/register': 'navRegister', '/addresses': 'navAddresses', '/security': 'navSecurity' };

export function routeHead(route, t) {
  if (route.startsWith('/products/')) return null;
  if (route === '/') return { description: t('metaHome'), path: '/' };
  if (route === '/help' || route.startsWith('/help/')) return { title: t('navHelp'), description: t('metaHelp'), path: '/help' };
  if (route === '/contact') return { title: t('contactTitle'), description: t('metaContact'), path: '/contact' };
  const policy = POLICIES[route.slice(1)];
  if (policy) return { title: t(policy.nav), path: route };
  const key = Object.keys(PRIVATE_TITLES).find(p => route === p || route.startsWith(`${p}/`));
  return { title: key ? t(PRIVATE_TITLES[key]) : route.startsWith('/admin') ? t('secAdmin') : undefined, index: false };
}

// data-testid attributes are the stable hooks used by Selenium (automation/.../pages/*.java).
function App() {
  const route = useRoute();
  const session = useSession();
  const [menuOpen, setMenuOpen] = useState(false);
  const { t } = useI18n();
  useHead(routeHead(route, t));
  const needsLogin = ['/cart', '/checkout', '/orders', '/wishlist', '/addresses', '/payment-methods', '/security', '/admin'].some(p => route.startsWith(p));

  useEffect(() => {
    const onKey = e => { if (e.key === 'Escape') setMenuOpen(false); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  let page;
  if (needsLogin && !session) page = <Login redirect={route} />;
  else if (route === '/login') page = <Login redirect="/" />;
  else if (route === '/register') page = <Login redirect="/" initialMode="register" />;
  else if (route === '/forgot-password') page = <ForgotPassword />;
  else if (route.startsWith('/reset-password')) page = <ResetPassword token={new URLSearchParams(route.split('?')[1] || '').get('token')} />;
  else if (route === '/help' || route.startsWith('/help/')) page = <Help />;
  else if (route === '/contact') page = <Contact />;
  else if (POLICIES[route.slice(1)]) page = <Legal page={route.slice(1)} />;
  else if (route === '/goodbye') page = <Goodbye session={session} />;
  else if (ADMIN[route]) { const P = ADMIN[route]; page = <AdminOnly session={session}><P /></AdminOnly>; }
  else if (route === '/cart') page = <Cart />;
  else if (route === '/checkout') page = <Checkout />;
  else if (route === '/orders') page = <Orders />;
  else if (route.startsWith('/orders/')) page = <OrderDetail id={route.split('/')[2]} />;
  else if (route === '/wishlist') page = <Wishlist />;
  else if (route === '/addresses') page = <Addresses />;
  else if (route === '/payment-methods') page = <PaymentMethods />;
  else if (route === '/security') page = <Security />;
  else if (route.startsWith('/products/')) page = <ProductDetail id={route.split('/')[2]} />;
  else page = <Home />;

  return (
    <div className="shell">
      <a className="skip" href="#main" onClick={e => { e.preventDefault(); document.getElementById('main')?.focus(); }}>Skip to content</a>
      {DEMO && <DemoRibbon />}
      <AnnounceBar />
      <Topbar session={session} menuOpen={menuOpen} onMenu={() => setMenuOpen(!menuOpen)} />
      <div className="frame">
        <Sidebar route={route} session={session} open={menuOpen} onNavigate={() => setMenuOpen(false)} />
        {menuOpen && <div className="scrim" aria-hidden="true" onClick={() => setMenuOpen(false)} />}
        <div className="content">
          <main id="main" tabIndex={-1}>{page}</main>
          <CookieNotice />
          <Footer session={session} />
        </div>
      </div>
    </div>
  );
}

installButtonIcons();
createRoot(document.getElementById('root')).render(<App />);

// Desktop version: the service worker makes the storefront installable and shows an offline page when the
// server is not running. Production build only, so development (vite dev) never serves a stale bundle.
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => navigator.serviceWorker.register('/sw.js').catch(() => {}));
}
