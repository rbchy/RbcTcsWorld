import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './style.css';
import { useRoute, useSession } from './session';
import { Footer, Sidebar, Topbar } from './Layout';
import { installButtonIcons } from './icons';
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
import Help from './pages/Help';
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

const ADMIN = {
  '/admin': AdminDashboard, '/admin/products': AdminProducts, '/admin/coupons': AdminCoupons,
  '/admin/orders': AdminOrders, '/admin/returns': AdminReturns, '/admin/reviews': AdminReviews,
  '/admin/notifications': AdminNotifications, '/admin/audit': AdminAudit,
  '/admin/customers': AdminCustomers, '/admin/payments': AdminPayments, '/admin/reports': AdminReports, '/admin/warehouses': AdminWarehouses,
};

/** Public demo only (built with VITE_DEMO=true, see deploy/): says plainly that nothing here is real. */
const DEMO = import.meta.env.VITE_DEMO === 'true';
function DemoRibbon() {
  const { t } = useI18n();
  return <p className="demo-ribbon" role="note" data-testid="demo-ribbon">{t('demoRibbon')}</p>;
}

// data-testid attributes are the stable hooks used by Selenium (automation/.../pages/*.java).
function App() {
  const route = useRoute();
  const session = useSession();
  const [menuOpen, setMenuOpen] = useState(false);
  const needsLogin = ['/cart', '/checkout', '/orders', '/wishlist', '/addresses', '/admin'].some(p => route.startsWith(p));

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
  else if (route === '/goodbye') page = <Goodbye session={session} />;
  else if (ADMIN[route]) { const P = ADMIN[route]; page = <AdminOnly session={session}><P /></AdminOnly>; }
  else if (route === '/cart') page = <Cart />;
  else if (route === '/checkout') page = <Checkout />;
  else if (route === '/orders') page = <Orders />;
  else if (route.startsWith('/orders/')) page = <OrderDetail id={route.split('/')[2]} />;
  else if (route === '/wishlist') page = <Wishlist />;
  else if (route === '/addresses') page = <Addresses />;
  else if (route.startsWith('/products/')) page = <ProductDetail id={route.split('/')[2]} />;
  else page = <Home />;

  return (
    <div className="shell">
      <a className="skip" href="#main" onClick={e => { e.preventDefault(); document.getElementById('main')?.focus(); }}>Skip to content</a>
      {DEMO && <DemoRibbon />}
      <Topbar session={session} menuOpen={menuOpen} onMenu={() => setMenuOpen(!menuOpen)} />
      <div className="frame">
        <Sidebar route={route} session={session} open={menuOpen} onNavigate={() => setMenuOpen(false)} />
        {menuOpen && <div className="scrim" aria-hidden="true" onClick={() => setMenuOpen(false)} />}
        <div className="content">
          <main id="main" tabIndex={-1}>{page}</main>
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
