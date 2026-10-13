import React, { useEffect, useState } from 'react';
import api, { errorText, money } from '../../api';
import { useI18n } from '../../i18n';
import { Field, Notice, when } from './common';
import ProductImage, { IMAGE_URL } from '../../ProductImage';

const PAGE = 25;
const EMPTY = { name: '', sku: '', category: '', price: '', stock: '', imageUrl: '' };

/** Same rules as the backend ProductRequest: name/sku required, price > 0 with 2 decimals, stock >= 0,
 *  picture empty, /products/<name>.svg|png|jpg|webp or an https:// link. */
export function validateProduct(f) {
  const e = {};
  if (!f.name.trim()) e.name = 'vRequired';
  if (!f.sku.trim()) e.sku = 'vRequired';
  if (f.price === '' || Number.isNaN(Number(f.price))) e.price = 'vRequired';
  else if (Number(f.price) <= 0) e.price = 'vPositive';
  else if (!/^\d+(\.\d{1,2})?$/.test(String(f.price).trim())) e.price = 'vMoney';
  if (f.stock === '' || !/^\d+$/.test(String(f.stock).trim())) e.stock = 'vWhole';
  const img = (f.imageUrl ?? '').trim();
  if (img && (img.length > 500 || !IMAGE_URL.test(img))) e.imageUrl = 'vImage';
  return e;
}

export default function AdminProducts() {
  const { t } = useI18n();
  const [rows, setRows] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [q, setQ] = useState('');
  const [form, setForm] = useState(null);         // null = closed, {id?...} = add or edit
  const [errors, setErrors] = useState({});
  const [confirm, setConfirm] = useState(null);   // product waiting for "Yes, delete"
  const [history, setHistory] = useState(null);   // {product, rows}
  const [ok, setOk] = useState('');
  const [error, setError] = useState('');

  const load = (p = page) => api.get('/products', { params: { q: q || undefined, page: p, size: PAGE, sort: 'id' } })
    .then(r => { setRows(r.data); setTotal(Number(r.headers['x-total-count'] ?? r.data.length)); setPage(p); })
    .catch(e => setError(errorText(e)));

  useEffect(() => {
    let current = true;
    const timer = setTimeout(() => current && load(0), q ? 250 : 0);
    return () => { current = false; clearTimeout(timer); };
  }, [q]);

  const open = p => {
    setErrors({}); setOk(''); setError(''); setConfirm(null);
    setForm(p ? { id: p.id, name: p.name, sku: p.sku, category: p.category || '', price: String(p.price), stock: String(p.stock), imageUrl: p.imageUrl || '' } : { ...EMPTY });
  };
  const set = k => e => {
    const next = { ...form, [k]: e.target.value };
    setForm(next);
    if (errors[k]) setErrors(validateProduct(next));
  };

  const save = async e => {
    e.preventDefault();
    const found = validateProduct(form);
    setErrors(found);
    if (Object.keys(found).length) return;
    const body = { name: form.name.trim(), sku: form.sku.trim(), category: form.category.trim() || null,
      price: Number(form.price), stock: Number(form.stock), imageUrl: form.imageUrl.trim() || null };
    try {
      if (form.id) await api.put(`/products/${form.id}`, body);
      else await api.post('/products', body);
      setOk(form.id ? t('productUpdated') : t('productSaved'));
      setError(''); setForm(null); load();
    } catch (err) { setOk(''); setError(errorText(err)); }
  };

  const remove = async p => {
    try { await api.delete(`/products/${p.id}`); setOk(t('productDeleted')); setError(''); setConfirm(null); load(); }
    catch (err) { setOk(''); setError(errorText(err)); }
  };

  const showHistory = async p => {
    try { setHistory({ product: p, rows: (await api.get(`/admin/products/${p.id}/stock-movements`)).data }); }
    catch (err) { setError(errorText(err)); }
  };

  const pages = Math.max(1, Math.ceil(total / PAGE));
  return (
    <section data-testid="admin-products">
      <header className="page-head">
        <h1>{t('productsTitle')}</h1>
        <div className="row">
          <input type="search" value={q} onChange={e => setQ(e.target.value)} placeholder={t('filter')} aria-label={t('filter')}
                 data-testid="admin-product-search" />
          <button onClick={() => open(null)} data-testid="admin-product-add">{t('addProduct')}</button>
        </div>
      </header>
      <Notice ok={ok} error={error} />

      {form && (
        <form className="card form-grid" onSubmit={save} noValidate data-testid="product-form" aria-labelledby="product-form-title">
          <h2 id="product-form-title">{form.id ? t('editProduct') : t('addProduct')}</h2>
          <Field id="pf-name" label={t('name')} error={errors.name}>
            <input value={form.name} onChange={set('name')} maxLength={255} data-testid="pf-name" />
          </Field>
          <Field id="pf-sku" label={t('sku')} error={errors.sku}>
            <input value={form.sku} onChange={set('sku')} maxLength={100} data-testid="pf-sku" />
          </Field>
          <Field id="pf-category" label={t('category')} error={errors.category}>
            <input value={form.category} onChange={set('category')} maxLength={100} data-testid="pf-category" />
          </Field>
          <Field id="pf-price" label={t('price')} error={errors.price}>
            <input value={form.price} onChange={set('price')} inputMode="decimal" data-testid="pf-price" />
          </Field>
          <Field id="pf-stock" label={t('stock')} error={errors.stock}>
            <input value={form.stock} onChange={set('stock')} inputMode="numeric" data-testid="pf-stock" />
          </Field>
          <div className="image-field">
            <Field id="pf-image" label={t('picture')} error={errors.imageUrl} hint={t('pictureHint')}>
              <input value={form.imageUrl} onChange={set('imageUrl')} maxLength={500} inputMode="url" spellCheck={false}
                     placeholder="/products/my-product.svg" data-testid="pf-image" />
            </Field>
            <ProductImage product={{ category: form.category, imageUrl: errors.imageUrl ? '' : form.imageUrl.trim() }}
                          className="pf-preview" testId="pf-image-preview" eager />
          </div>
          <div className="row form-actions">
            <button type="submit" data-testid="pf-save">{form.id ? t('updateProduct') : t('saveProduct')}</button>
            <button type="button" className="ghost" onClick={() => setForm(null)} data-testid="pf-cancel">{t('cancel')}</button>
          </div>
        </form>
      )}

      <div className="table-wrap card">
        <table data-testid="admin-product-table">
          <thead><tr><th><span className="sr-only">{t('picture')}</span></th><th>{t('sku')}</th><th>{t('name')}</th><th>{t('category')}</th><th className="num">{t('price')}</th>
            <th className="num">{t('stock')}</th><th>{t('actions')}</th></tr></thead>
          <tbody>
            {rows.length === 0 && <tr><td colSpan="7" className="muted">{t('noRows')}</td></tr>}
            {rows.map(p => (
              <React.Fragment key={p.id}>
                <tr data-testid="admin-product-row" data-sku={p.sku}>
                  <td className="pic"><ProductImage product={p} className="row-thumb" testId="apr-image" /></td>
                  <td>{p.sku}</td>
                  <td data-testid="apr-name">{p.name}</td>
                  <td>{p.category}</td>
                  <td className="num" data-testid="apr-price">{money(p.price)}</td>
                  <td className={`num${p.stock <= 5 ? ' low' : ''}`} data-testid="apr-stock">{p.stock}</td>
                  <td className="actions">
                    <button className="link" onClick={() => open(p)} data-testid="apr-edit">{t('edit')}</button>
                    <button className="link" onClick={() => showHistory(p)} data-testid="apr-history">{t('stockHistory')}</button>
                    <button className="link danger" onClick={() => { setOk(''); setError(''); setConfirm(p); }} data-testid="apr-delete">{t('delete')}</button>
                  </td>
                </tr>
                {confirm?.id === p.id && (
                  <tr className="confirm-row" data-testid="delete-confirm">
                    <td colSpan="7">
                      <span>{t('confirmDelete', { name: p.name })}</span>
                      <button className="danger-btn" onClick={() => remove(p)} data-testid="apr-delete-yes">{t('yesDelete')}</button>
                      <button className="ghost" onClick={() => setConfirm(null)}>{t('cancel')}</button>
                    </td>
                  </tr>
                )}
              </React.Fragment>
            ))}
          </tbody>
        </table>
        <div className="row between pager">
          <span className="muted small" data-testid="admin-product-total">{t('showing', { n: rows.length, total })}</span>
          <span className="row">
            <button className="ghost" disabled={page === 0} onClick={() => load(page - 1)} aria-label="Previous page">‹</button>
            <span className="small">{page + 1} / {pages}</span>
            <button className="ghost" disabled={page + 1 >= pages} onClick={() => load(page + 1)} aria-label="Next page">›</button>
          </span>
        </div>
      </div>

      {history && (
        <section className="card" data-testid="stock-history" aria-labelledby="history-title">
          <div className="row between">
            <h2 id="history-title">{t('stockHistory')}: {history.product.name}</h2>
            <button className="ghost" onClick={() => setHistory(null)}>{t('close')}</button>
          </div>
          <table>
            <thead><tr><th>{t('date')}</th><th>{t('reason')}</th><th className="num">{t('change')}</th><th className="num">{t('stockAfter')}</th></tr></thead>
            <tbody>
              {history.rows.length === 0 && <tr><td colSpan="4" className="muted">{t('noRows')}</td></tr>}
              {history.rows.map(m => (
                <tr key={m.id}><td>{when(m.createdAt)}</td><td>{m.reason}</td>
                  <td className="num">{m.changeQty > 0 ? `+${m.changeQty}` : m.changeQty}</td><td className="num">{m.stockAfter}</td></tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </section>
  );
}
