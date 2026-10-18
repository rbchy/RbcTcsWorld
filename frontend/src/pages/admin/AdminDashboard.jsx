import React, { useEffect, useState } from 'react';
import api, { errorText, money } from '../../api';
import { useI18n } from '../../i18n';
import { Notice, when } from './common';
import Watermark from '../../Watermark';
import { getSession } from '../../session';
import { Icon } from '../../icons';
import { isoDay } from '../../DateInput';
import { DayBars, Donut, MonthHeat, STATUS_COLORS } from '../../charts';
import { ORDER_STATUSES } from './common';

const PAID_LIKE = ['PAID', 'SHIPPED', 'DELIVERED', 'RETURN_REQUESTED'];

export default function AdminDashboard() {
  const { t, lang } = useI18n();
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
    ['statOrders', data.orders.length, '#/admin/orders', 'stat-orders', 'receipt', 'c-blue'],
    ['statToShip', toShip.length, '#/admin/orders', 'stat-to-ship', 'truck', 'c-amber'],
    ['statRevenue', money(revenue), '#/admin/orders', 'stat-revenue', 'chart', 'c-green'],
    ['statLowStock', low.length, '#/admin/products', 'stat-low-stock', 'box', 'c-red'],
    ['statReturns', data.returns.length, '#/admin/returns', 'stat-returns', 'returnArrow', 'c-violet'],
  ];
  // graphics: money received per day (last 14 days), orders by status, orders per day this month
  const day = o => (o.createdAt ? isoDay(new Date(o.createdAt)) : '');
  const last14 = Array.from({ length: 14 }, (_, i) => { const d = new Date(); d.setDate(d.getDate() - 13 + i); return isoDay(d); });
  const salesByDay = last14.map(d => ({ day: d, value: data.orders.filter(o => PAID_LIKE.includes(o.status) && day(o) === d)
    .reduce((s, o) => s + Number(o.total), 0) }));
  const byStatus = ORDER_STATUSES.map(st => ({ key: st, label: t(`st_${st}`), color: STATUS_COLORS[st],
    value: data.orders.filter(o => o.status === st).length }));
  const perDay = data.orders.reduce((m, o) => { const d = day(o); if (d) m[d] = (m[d] || 0) + 1; return m; }, {});

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
        {stats.map(([key, value, href, id, icon, color]) => (
          <li key={key} className={`stat ${color}`}>
            <a href={href} data-testid={id}>
              <span className="stat-icon" aria-hidden="true"><Icon name={icon} size={22} /></span>
              <strong>{value}</strong><span>{t(key)}</span>
            </a>
          </li>
        ))}
      </ul>
      <div className="dash-charts">
        <section className="card chart-card" aria-labelledby="ch-sales">
          <h2 id="ch-sales"><Icon name="chart" />{t('chSales14')}</h2>
          <DayBars days={salesByDay} label={t('chSales14')} format={money} testId="chart-sales" />
        </section>
        <section className="card chart-card" aria-labelledby="ch-status">
          <h2 id="ch-status"><Icon name="receipt" />{t('chByStatus')}</h2>
          <Donut parts={byStatus} label={t('chByStatus')} centre={t('statOrders')} testId="chart-status" />
        </section>
        <section className="card chart-card" aria-labelledby="ch-month">
          <h2 id="ch-month"><Icon name="calendar" />{t('chThisMonth')}</h2>
          <MonthHeat counts={perDay} lang={lang} label={t('chThisMonth')} legendFew={t('chFew')} legendMany={t('chMany')} testId="chart-month" />
        </section>
      </div>
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
