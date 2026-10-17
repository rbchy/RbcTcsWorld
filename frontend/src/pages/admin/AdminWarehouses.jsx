import React, { useEffect, useState } from 'react';
import api, { errorText } from '../../api';
import { useI18n } from '../../i18n';
import { Field, Notice } from './common';

const EMPTY = { code: '', name: '', city: '', state: '', priority: '100', active: true };
const REASONS = ['RECEIVED', 'COUNT_CORRECTION', 'DAMAGED'];

/** Same rules as the API (WarehouseDtos): code 2-10 letters/digits, 2-letter state, priority 1-999. */
export function validateWarehouse(f) {
  const e = {};
  if (!/^[A-Za-z0-9]{2,10}$/.test(f.code.trim())) e.code = 'vWhCode';
  if (!f.name.trim()) e.name = 'vRequired';
  if (!f.city.trim()) e.city = 'vRequired';
  if (!/^[A-Za-z]{2}$/.test(f.state.trim())) e.state = 'vUsState';
  if (!/^\d+$/.test(String(f.priority).trim()) || +f.priority < 1 || +f.priority > 999) e.priority = 'vPriority';
  return e;
}

/** Adjust: a whole number, not 0; received goods add, damage removes. Transfer: two warehouses, a positive quantity. */
export function validateMove(kind, f, held) {
  const e = {};
  if (kind === 'adjust') {
    if (!/^-?\d+$/.test(String(f.delta).trim()) || +f.delta === 0) e.delta = 'vNonZero';
    else if (f.reason === 'RECEIVED' && +f.delta < 0) e.delta = 'vReceivedAdds';
    else if (f.reason === 'DAMAGED' && +f.delta > 0) e.delta = 'vDamagedRemoves';
    else if (+f.delta < 0 && -f.delta > held(f.warehouseId)) e.delta = 'vMoreThanHeld';
  } else {
    if (f.from === f.to) e.to = 'vSameWarehouse';
    if (!/^\d+$/.test(String(f.quantity).trim()) || +f.quantity < 1) e.quantity = 'vPositive';
    else if (+f.quantity > held(f.from)) e.quantity = 'vMoreThanHeld';
  }
  return e;
}

/** Warehouses and the stock in each: receive goods, correct a count, write off damage, move stock. */
export default function AdminWarehouses() {
  const { t } = useI18n();
  const [rows, setRows] = useState([]);
  const [form, setForm] = useState(null);                 // null = closed; {id?} = add / edit
  const [errors, setErrors] = useState({});
  const [q, setQ] = useState('');
  const [found, setFound] = useState([]);
  const [stock, setStock] = useState(null);               // ProductStock of the chosen product
  const [adj, setAdj] = useState({ warehouseId: '', delta: '', reason: 'RECEIVED' });
  const [tr, setTr] = useState({ from: '', to: '', quantity: '' });
  const [moveErrors, setMoveErrors] = useState({});
  const [ok, setOk] = useState('');
  const [error, setError] = useState('');

  const load = () => api.get('/admin/warehouses').then(r => setRows(r.data)).catch(e => setError(errorText(e)));
  useEffect(() => { load(); }, []);

  const done = msg => { setOk(msg); setError(''); };
  const fail = err => { setOk(''); setError(errorText(err)); };

  const open = w => {
    setErrors({}); setOk(''); setError('');
    setForm(w ? { id: w.id, code: w.code, name: w.name, city: w.city, state: w.state, priority: String(w.priority), active: w.active }
      : { ...EMPTY });
  };
  const set = k => e => {
    const next = { ...form, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value };
    setForm(next);
    if (errors[k]) setErrors(validateWarehouse(next));
  };
  const save = async e => {
    e.preventDefault();
    const f = validateWarehouse(form);
    setErrors(f);
    if (Object.keys(f).length) return;
    const body = { name: form.name.trim(), city: form.city.trim(), state: form.state.trim().toUpperCase(), priority: Number(form.priority) };
    try {
      if (form.id) await api.put(`/admin/warehouses/${form.id}`, { ...body, active: form.active });
      else await api.post('/admin/warehouses', { ...body, code: form.code.trim().toUpperCase() });
      done(t('warehouseSaved')); setForm(null); load();
    } catch (err) { fail(err); }
  };

  const search = async e => {
    e.preventDefault();
    try { setFound((await api.get('/products', { params: { q: q.trim() || undefined, size: 10 } })).data); }
    catch (err) { fail(err); }
  };
  const choose = async id => {
    try {
      const r = await api.get(`/admin/products/${id}/stock`);
      setStock(r.data); setMoveErrors({});
      const active = r.data.warehouses.filter(w => w.active);
      setAdj({ warehouseId: String(active[0]?.warehouseId ?? ''), delta: '', reason: 'RECEIVED' });
      setTr({ from: String(r.data.warehouses[0]?.warehouseId ?? ''), to: String(active[1]?.warehouseId ?? active[0]?.warehouseId ?? ''), quantity: '' });
    } catch (err) { fail(err); }
  };
  const held = id => stock?.warehouses.find(w => String(w.warehouseId) === String(id))?.quantity ?? 0;

  const move = kind => async e => {
    e.preventDefault();
    const f = validateMove(kind, kind === 'adjust' ? adj : tr, held);
    setMoveErrors(f);
    if (Object.keys(f).length) return;
    try {
      const r = kind === 'adjust'
        ? await api.post('/admin/stock/adjust', { productId: stock.productId, warehouseId: Number(adj.warehouseId), delta: Number(adj.delta), reason: adj.reason })
        : await api.post('/admin/stock/transfer', { productId: stock.productId, fromWarehouseId: Number(tr.from), toWarehouseId: Number(tr.to), quantity: Number(tr.quantity) });
      setStock(r.data);
      if (kind === 'adjust') setAdj({ ...adj, delta: '' }); else setTr({ ...tr, quantity: '' });
      done(kind === 'adjust' ? t('stockAdjusted') : t('stockTransferred')); load();
    } catch (err) { fail(err); }
  };

  const fieldError = k => moveErrors[k] && <span className="field-error" id={`mv-${k}-error`} data-testid={`mv-${k}-error`}>{t(moveErrors[k])}</span>;
  const activeOptions = (stock?.warehouses || []).filter(w => w.active);

  return (
    <section data-testid="admin-warehouses">
      <header className="page-head">
        <h1>{t('warehousesTitle')}</h1>
        <button onClick={() => open(null)} data-testid="warehouse-add">{t('addWarehouse')}</button>
      </header>
      <p className="muted small">{t('warehousesHint')}</p>
      <Notice ok={ok} error={error} />

      {form && (
        <form className="card form-grid" onSubmit={save} noValidate data-testid="warehouse-form" aria-labelledby="wh-form-title">
          <h2 id="wh-form-title">{form.id ? `${t('edit')}: ${form.code}` : t('addWarehouse')}</h2>
          <Field id="wf-code" label={t('code')} error={errors.code}>
            <input value={form.code} onChange={set('code')} maxLength={10} readOnly={!!form.id} data-testid="wf-code" />
          </Field>
          <Field id="wf-name" label={t('name')} error={errors.name}>
            <input value={form.name} onChange={set('name')} maxLength={80} data-testid="wf-name" />
          </Field>
          <Field id="wf-city" label={t('city')} error={errors.city}>
            <input value={form.city} onChange={set('city')} maxLength={60} data-testid="wf-city" />
          </Field>
          <Field id="wf-state" label={t('col_usState')} error={errors.state}>
            <input value={form.state} onChange={set('state')} maxLength={2} data-testid="wf-state" />
          </Field>
          <Field id="wf-priority" label={t('priority')} error={errors.priority} hint={t('priorityHint')}>
            <input value={form.priority} onChange={set('priority')} inputMode="numeric" data-testid="wf-priority" />
          </Field>
          {form.id && (
            <label className="check field-check">
              <input type="checkbox" checked={form.active} onChange={set('active')} data-testid="wf-active" />{t('warehouseActive')}
            </label>
          )}
          <div className="row form-actions">
            <button type="submit" data-testid="wf-save">{t('saveChanges')}</button>
            <button type="button" className="ghost" onClick={() => setForm(null)}>{t('cancel')}</button>
          </div>
        </form>
      )}

      <div className="table-wrap card" tabIndex={0} role="region" aria-label={t('warehousesTitle')}>
        <table data-testid="warehouse-table">
          <thead><tr><th>{t('code')}</th><th>{t('name')}</th><th>{t('city')}</th><th className="num">{t('priority')}</th>
            <th className="num">{t('col_units')}</th><th>{t('status')}</th><th>{t('actions')}</th></tr></thead>
          <tbody>
            {rows.map(w => (
              <tr key={w.id} data-testid="warehouse-row" data-code={w.code}>
                <td><strong>{w.code}</strong></td><td>{w.name}</td><td>{w.city}, {w.state}</td>
                <td className="num">{w.priority}</td><td className="num" data-testid="warehouse-units">{w.units}</td>
                <td><span className={`badge ${w.active ? 'state-active' : 'state-disabled'}`}>{w.active ? t('accountActive') : t('csDISABLED')}</span></td>
                <td className="actions"><button type="button" className="ghost" onClick={() => open(w)} data-testid="warehouse-edit"
                  aria-label={`${t('edit')} ${w.code}`}>{t('edit')}</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <h2>{t('stockPerWarehouse')}</h2>
      <form className="card filters" role="search" aria-label={t('stockPerWarehouse')} onSubmit={search} data-testid="stock-search">
        <label className="field">{t('filter')}
          <input type="search" value={q} onChange={e => setQ(e.target.value)} data-testid="ss-q" />
        </label>
        <div className="filter-actions"><button type="submit" data-testid="ss-search">{t('search')}</button></div>
      </form>
      {found.length > 0 && (
        <ul className="chips" data-testid="stock-found">
          {found.map(p => (
            <li key={p.id}><button type="button" className={stock?.productId === p.id ? '' : 'ghost'} onClick={() => choose(p.id)}
              data-testid="stock-pick">{p.sku} · {p.name}</button></li>
          ))}
        </ul>
      )}

      {stock && (
        <section className="card" aria-labelledby="stock-title" data-testid="stock-panel">
          <h3 id="stock-title">{stock.sku} · {stock.name} — <span data-testid="stock-total">{t('stockTotal', { n: stock.total })}</span></h3>
          <table data-testid="stock-table">
            <thead><tr><th>{t('warehouse')}</th><th>{t('city')}</th><th className="num">{t('col_units')}</th></tr></thead>
            <tbody>{stock.warehouses.map(w => (
              <tr key={w.warehouseId} data-testid="stock-row" data-code={w.code} className={w.active ? '' : 'muted'}>
                <td>{w.code}{w.active ? '' : ` (${t('csDISABLED')})`}</td><td>{w.city}, {w.state}</td>
                <td className="num" data-testid="stock-qty">{w.quantity}</td></tr>
            ))}</tbody>
          </table>

          <div className="two-col">
            <form onSubmit={move('adjust')} noValidate data-testid="adjust-form" aria-labelledby="adjust-title">
              <h4 id="adjust-title">{t('adjustStock')}</h4>
              <label className="field">{t('warehouse')}
                <select value={adj.warehouseId} onChange={e => setAdj({ ...adj, warehouseId: e.target.value })} data-testid="adj-warehouse">
                  {stock.warehouses.map(w => <option key={w.warehouseId} value={w.warehouseId}>{w.code} ({w.quantity})</option>)}
                </select>
              </label>
              <label className="field">{t('reason')}
                <select value={adj.reason} onChange={e => setAdj({ ...adj, reason: e.target.value })} data-testid="adj-reason">
                  {REASONS.map(r => <option key={r} value={r}>{t(`mv_${r}`)}</option>)}
                </select>
              </label>
              <div className="field">
                <label htmlFor="adj-delta">{t('changeUnits')}</label>
                <input id="adj-delta" value={adj.delta} onChange={e => setAdj({ ...adj, delta: e.target.value })} inputMode="numeric"
                       data-testid="adj-delta" aria-invalid={!!moveErrors.delta} aria-describedby={moveErrors.delta ? 'mv-delta-error' : undefined} />
                {fieldError('delta')}
              </div>
              <button type="submit" data-testid="adj-save">{t('adjustStock')}</button>
            </form>

            <form onSubmit={move('transfer')} noValidate data-testid="transfer-form" aria-labelledby="transfer-title">
              <h4 id="transfer-title">{t('transferStock')}</h4>
              <label className="field">{t('fromWarehouse')}
                <select value={tr.from} onChange={e => setTr({ ...tr, from: e.target.value })} data-testid="tr-from">
                  {stock.warehouses.map(w => <option key={w.warehouseId} value={w.warehouseId}>{w.code} ({w.quantity})</option>)}
                </select>
              </label>
              <div className="field">
                <label htmlFor="tr-to">{t('toWarehouse')}</label>
                <select id="tr-to" value={tr.to} onChange={e => setTr({ ...tr, to: e.target.value })} data-testid="tr-to"
                        aria-invalid={!!moveErrors.to} aria-describedby={moveErrors.to ? 'mv-to-error' : undefined}>
                  {activeOptions.map(w => <option key={w.warehouseId} value={w.warehouseId}>{w.code} ({w.quantity})</option>)}
                </select>
                {fieldError('to')}
              </div>
              <div className="field">
                <label htmlFor="tr-qty">{t('quantity')}</label>
                <input id="tr-qty" value={tr.quantity} onChange={e => setTr({ ...tr, quantity: e.target.value })} inputMode="numeric"
                       data-testid="tr-qty" aria-invalid={!!moveErrors.quantity} aria-describedby={moveErrors.quantity ? 'mv-quantity-error' : undefined} />
                {fieldError('quantity')}
              </div>
              <button type="submit" data-testid="tr-save">{t('transferStock')}</button>
            </form>
          </div>
        </section>
      )}
    </section>
  );
}
