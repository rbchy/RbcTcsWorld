import React, { useEffect, useState } from 'react';
import api, { errorText, money } from '../../api';
import { useI18n } from '../../i18n';
import { Notice, when } from './common';
import Watermark from '../../Watermark';
import { getSession } from '../../session';

const PAID_LIKE = ['PAID', 'SHIPPED', 'DELIVERED', 'RETURN_REQUESTED'];

export default function AdminDashboard() {
  const { t } = useI18n();
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([
      api.get('/admin/orders'),
      api.get('/admin/returns', { params: { status: 'REQUESTED' } }),
      api.get('/products', { params: { page: 0, size: 100, sort: 'id' } }),
    ]).then(([orders, returns, products]) => setData({
      orders: orders.data, returns: returns.data, products: products.data,
    })).catch(e => setError(errorText(e)));
  }, []);

  if (error) return <Notice error={error} />;
  if (!data) return <p>{t('loading')}</p>;
  const toShip = data.orders.filter(o => o.status === 'PAID');
  const revenue = data.orders.filter(o => PAID_LIKE.includes(o.status)).reduce((s, o) => s + Number(o.total), 0);
  const low = data.products.filter(p => p.stock <= 5);
  const stats = [
    ['statOrders', data.orders.length, '#/admin/orders', 'stat-orders'],
    ['statToShip', toShip.length, '#/admin/orders', 'stat-to-ship'],
    ['statRevenue', money(revenue), '#/admin/orders', 'stat-revenue'],
    ['statLowStock', low.length, '#/admin/products', 'stat-low-stock'],
    ['statReturns', data.returns.length, '#/admin/returns', 'stat-returns'],
  ];

  return (
    <section data-testid="admin-dashboard">
      <header className="dash-banner" data-testid="dashboard-banner">
        <Watermark tone="dark" />
        <div className="dash-banner-body">
          <p className="dash-date">{new Date().toLocaleDateString(undefined, { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}</p>
          <h1>{t('dashTitle')}</h1>
          <p className="dash-who">{t('dashWelcome', { email: getSession()?.email || '' })}</p>
        </div>
        <nav className="dash-quick" aria-label={t('dashQuick')}>
          <a href="#/admin/products" data-testid="dash-add-product">{t('dashAddProduct')}</a>
          <a href="#/admin/orders" data-testid="dash-ship">{t('dashShipOrders', { n: toShip.length })}</a>
          <a href="#/admin/reports" data-testid="dash-reports">{t('navReports')}</a>
        </nav>
      </header>
      <ul className="stats">
        {stats.map(([key, value, href, id]) => (
          <li key={key}><a href={href} data-testid={id}><strong>{value}</strong><span>{t(key)}</span></a></li>
        ))}
      </ul>
      <div className="two-col">
        <section className="card" aria-labelledby="dash-ship">
          <h2 id="dash-ship">{t('statToShip')}</h2>
          {toShip.length === 0 ? <p className="muted">{t('noRows')}</p> : (
            <table><tbody>{toShip.slice(-8).reverse().map(o => (
              <tr key={o.id}><td>{o.orderNumber}</td><td className="small">{when(o.paidAt)}</td><td className="num">{money(o.total)}</td></tr>
            ))}</tbody></table>
          )}
        </section>
        <section className="card" aria-labelledby="dash-low">
          <h2 id="dash-low">{t('statLowStock')}</h2>
          {low.length === 0 ? <p className="muted">{t('noRows')}</p> : (
            <table><tbody>{low.slice(0, 8).map(p => (
              <tr key={p.id}><td>{p.sku}</td><td>{p.name}</td><td className="num low">{p.stock}</td></tr>
            ))}</tbody></table>
          )}
        </section>
      </div>
    </section>
  );
}
