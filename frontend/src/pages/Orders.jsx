import React, { useEffect, useState } from 'react';
import api, { errorText, money } from '../api';
import { useI18n } from '../i18n';

export default function Orders() {
  const { t } = useI18n();
  const [orders, setOrders] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => { api.get('/orders').then(r => setOrders(r.data)).catch(e => setError(errorText(e))); }, []);

  if (error) return <p className="error">{error}</p>;
  if (!orders) return <p>Loading orders...</p>;
  return (
    <section className="card">
      <h1>{t('ordersTitle')}</h1>
      {orders.length === 0 ? <p data-testid="orders-empty">No orders yet.</p> : (
        <table data-testid="orders-table">
          <thead><tr><th>Order</th><th>Status</th><th>Items</th><th>Total</th></tr></thead>
          <tbody>
            {orders.map(o => (
              <tr key={o.id} data-testid="order-row" data-order-id={o.id}>
                <td><a href={`#/orders/${o.id}`} data-testid="order-link">{o.orderNumber}</a></td>
                <td><span className={`badge ${o.status}`} data-testid="order-row-status">{o.status}</span></td>
                <td>{o.totalQuantity}</td>
                <td>{money(o.total)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
