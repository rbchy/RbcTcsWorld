import React, { useEffect, useState } from 'react';
import api, { errorText, money } from '../../api';
import { useI18n } from '../../i18n';
import { Field, Notice, when } from './common';
import ProductImage, { IMAGE_URL } from '../../ProductImage';
import { ACCEPT, PhotoError, preparePhoto, uploadName } from '../../photo';
import { partnerName } from '../../Partner';

/** The public demo build (VITE_DEMO=true): photo uploads are off there (the backend refuses them too, ADR-0017). */
const DEMO = import.meta.env.VITE_DEMO === 'true';

const PAGE = 25;
const EMPTY = { name: '', sku: '', category: '', price: '', stock: '', imageUrl: '', variantGroup: '', size: '', color: '', dealPrice: '', dealStartsAt: '', dealEndsAt: '', affiliateUrl: '' };

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
  const aff = (f.affiliateUrl ?? '').trim();
  if (aff && (aff.length > 500 || !/^https:\/\/[^\s"'<>]+$/.test(aff))) e.affiliateUrl = 'vHttps';
  // variants (same rules as ProductService): a size or colour needs a group, a group needs a size or colour
  const group = (f.variantGroup ?? '').trim();
  const size = (f.size ?? '').trim();
  const color = (f.color ?? '').trim();
  if (group && !/^[A-Za-z0-9][A-Za-z0-9-]{1,39}$/.test(group)) e.variantGroup = 'vGroup';
  if (size && !/^[\p{L}\p{N} ./-]{1,20}$/u.test(size)) e.size = 'vSize';
  if (color && !/^[\p{L} -]{1,30}$/u.test(color)) e.color = 'vColor';
  if (!group && (size || color)) e.variantGroup = 'vGroupNeeded';
  if (group && !e.variantGroup && !size && !color) e.size = 'vSizeOrColor';
  // flash deal (same rules as ProductService.checkDeal): all three or none, lower than the price, end after start
  const dp = String(f.dealPrice ?? '').trim();
  const ds = f.dealStartsAt ?? '';
  const de = f.dealEndsAt ?? '';
  if (dp || ds || de) {
    if (!dp || !ds || !de) e.dealPrice = 'vDealAll';
    else if (!/^\d+(\.\d{1,2})?$/.test(dp) || Number(dp) <= 0) e.dealPrice = 'vMoney';
    else if (!e.price && Number(dp) >= Number(f.price)) e.dealPrice = 'vDealLower';
    if (ds && de && de <= ds) e.dealEndsAt = 'vDealOrder';
  }
  return e;
}

const NO_FILTER = { category: '', minPrice: '', maxPrice: '', inStock: false, sort: 'id' };

/** Same rules as ProductService: prices are 0 or more with at most 2 decimals, max not below min. */
export function validateProductSearch(f) {
  const e = {};
  const money = /^\d+(\.\d{1,2})?$/;
  if (f.minPrice.trim() && !money.test(f.minPrice.trim())) e.minPrice = 'vMoney';
  if (f.maxPrice.trim() && !money.test(f.maxPrice.trim())) e.maxPrice = 'vMoney';
  if (!e.minPrice && !e.maxPrice && f.minPrice.trim() && f.maxPrice.trim() && Number(f.maxPrice) < Number(f.minPrice)) {
    e.maxPrice = 'vMaxBelowMin';
  }
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
  const [adv, setAdv] = useState(NO_FILTER);            // advanced search being typed
  const [applied, setApplied] = useState(NO_FILTER);    // advanced search in use
  const [advErrors, setAdvErrors] = useState({});
  const [categories, setCategories] = useState([]);
  const [photo, setPhoto] = useState({});          // {busy} | {ok} | {error} for the photo upload

  useEffect(() => { api.get('/products/categories').then(r => setCategories(r.data)).catch(() => {}); }, []);

  const load = (p = page) => api.get('/products', { params: { q: q || undefined, page: p, size: PAGE, sort: applied.sort,
    category: applied.category || undefined, minPrice: applied.minPrice.trim() || undefined,
    maxPrice: applied.maxPrice.trim() || undefined, inStock: applied.inStock || undefined } })
    .then(r => { setRows(r.data); setTotal(Number(r.headers['x-total-count'] ?? r.data.length)); setPage(p); })
    .catch(e => setError(errorText(e)));

  useEffect(() => {
    let current = true;
    const timer = setTimeout(() => current && load(0), q ? 250 : 0);
    return () => { current = false; clearTimeout(timer); };
  }, [q, applied]);

  const setA = k => e => setAdv({ ...adv, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });
  const searchAdv = e => {
    e.preventDefault();
    const found = validateProductSearch(adv);
    setAdvErrors(found);
    if (!Object.keys(found).length) setApplied(adv);
  };

  const open = p => {
    setErrors({}); setOk(''); setError(''); setConfirm(null); setPhoto({});
    setForm(p ? { id: p.id, name: p.name, sku: p.sku, category: p.category || '', price: String(p.price), stock: String(p.stock), imageUrl: p.imageUrl || '',
      variantGroup: p.variantGroup || '', size: p.size || '', color: p.color || '',
      dealPrice: p.dealPrice != null ? String(p.dealPrice) : '', dealStartsAt: (p.dealStartsAt || '').slice(0, 16), dealEndsAt: (p.dealEndsAt || '').slice(0, 16),
      affiliateUrl: p.affiliateUrl || '' } : { ...EMPTY });
  };
  const set = k => e => {
    const next = { ...form, [k]: e.target.value };
    setForm(next);
    if (errors[k]) setErrors(validateProduct(next));
  };

  /** Prepares the chosen photo in the browser, uploads it and puts its address into the picture field. */
  const uploadPhoto = async e => {
    const file = e.target.files?.[0];
    e.target.value = '';                      // choosing the same file again uploads it again
    if (!file) return;
    setPhoto({ busy: true });
    try {
      const blob = await preparePhoto(file);
      const data = new FormData();
      data.append('file', blob, uploadName(blob, file));
      const r = await api.post('/products/pictures', data);
      setForm(f => ({ ...f, imageUrl: r.data.url }));
      setErrors(({ imageUrl, ...rest }) => rest);
      setPhoto({ ok: t('photoUploaded') });
    } catch (err) {
      setPhoto({ error: err instanceof PhotoError ? t(err.key) : err.response?.status === 413 ? t('photoTooBig') : errorText(err) });
    }
  };

  const save = async e => {
    e.preventDefault();
    const found = validateProduct(form);
    setErrors(found);
    if (Object.keys(found).length) return;
    const body = { name: form.name.trim(), sku: form.sku.trim(), category: form.category.trim() || null,
      price: Number(form.price), stock: Number(form.stock), imageUrl: form.imageUrl.trim() || null,
      variantGroup: form.variantGroup.trim() || null, size: form.size.trim() || null, color: form.color.trim() || null,
      dealPrice: form.dealPrice.trim() ? Number(form.dealPrice) : null,
      dealStartsAt: form.dealStartsAt ? `${form.dealStartsAt}:00` : null, dealEndsAt: form.dealEndsAt ? `${form.dealEndsAt}:00` : null,
      affiliateUrl: form.affiliateUrl.trim() || null };
    try {
      if (form.id) await api.put(`/products/${form.id}`, body);
      else await api.post('/products', body);
      setOk(form.id ? t('productUpdated') : t('productSaved'));
      setError(''); setForm(null); load();
      // the form is long (picture, photo, deal): back to the top, where the notice and the list are
      window.scrollTo(0, 0);
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

      <form className="card filters" role="search" aria-label={t('advancedSearch')} onSubmit={searchAdv} noValidate
            data-testid="product-adv-search">
        <label className="field">{t('category')}
          <select value={adv.category} onChange={setA('category')} data-testid="ps-category">
            <option value="">{t('allCategories')}</option>
            {categories.map(c => <option key={c} value={c}>{c}</option>)}
          </select>
        </label>
        {[['minPrice', 'ps-min', t('minPrice')], ['maxPrice', 'ps-max', t('maxPrice')]].map(([k, id, label]) => (
          <div className="field" key={k}>
            <label htmlFor={id}>{label}</label>
            <input id={id} value={adv[k]} onChange={setA(k)} inputMode="decimal" data-testid={id} aria-invalid={!!advErrors[k]}
                   aria-describedby={advErrors[k] ? `${id}-error` : undefined} />
            {advErrors[k] && <span className="field-error" id={`${id}-error`} data-testid={`${id}-error`}>{t(advErrors[k])}</span>}
          </div>
        ))}
        <label className="field">{t('sortBy')}
          <select value={adv.sort} onChange={setA('sort')} data-testid="ps-sort">
            {['id', 'name', 'price_asc', 'price_desc'].map(o => <option key={o} value={o}>{t(`psSort_${o}`)}</option>)}
          </select>
        </label>
        <label className="check field-check">
          <input type="checkbox" checked={adv.inStock} onChange={setA('inStock')} data-testid="ps-instock" />{t('inStockOnly')}
        </label>
        <div className="filter-actions">
          <button type="submit" data-testid="ps-search">{t('search')}</button>
          <button type="button" className="ghost" data-testid="ps-reset"
                  onClick={() => { setAdv(NO_FILTER); setAdvErrors({}); setApplied(NO_FILTER); }}>{t('reset')}</button>
        </div>
      </form>

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
          <fieldset className="variant-fields">
            <legend>{t('variantLegend')}</legend>
            <p className="hint">{t('variantHint')}</p>
            <Field id="pf-group" label={t('variantGroup')} error={errors.variantGroup}>
              <input value={form.variantGroup} onChange={set('variantGroup')} maxLength={40} placeholder="TEE-CLASSIC"
                     autoCapitalize="characters" spellCheck={false} data-testid="pf-group" />
            </Field>
            <Field id="pf-size" label={t('sizeLabel')} error={errors.size}>
              <input value={form.size} onChange={set('size')} maxLength={20} placeholder="M" list="pf-size-list" data-testid="pf-size" />
            </Field>
            <datalist id="pf-size-list">{['XS', 'S', 'M', 'L', 'XL', 'XXL'].map(x => <option key={x} value={x} />)}</datalist>
            <Field id="pf-color" label={t('colorLabel')} error={errors.color}>
              <input value={form.color} onChange={set('color')} maxLength={30} placeholder="Navy" data-testid="pf-color" />
            </Field>
          </fieldset>
          <fieldset className="deal-fields">
            <legend>{t('dealLegend')}</legend>
            <p className="hint">{t('dealHint')}</p>
            <Field id="pf-deal-price" label={t('dealPriceLabel')} error={errors.dealPrice}>
              <input value={form.dealPrice} onChange={set('dealPrice')} inputMode="decimal" data-testid="pf-deal-price" />
            </Field>
            <Field id="pf-deal-start" label={t('dealStartLabel')} error={errors.dealStartsAt}>
              <input type="datetime-local" value={form.dealStartsAt} onChange={set('dealStartsAt')} data-testid="pf-deal-start" />
            </Field>
            <Field id="pf-deal-end" label={t('dealEndLabel')} error={errors.dealEndsAt}>
              <input type="datetime-local" value={form.dealEndsAt} onChange={set('dealEndsAt')} data-testid="pf-deal-end" />
            </Field>
          </fieldset>
          <fieldset className="deal-fields partner-fields">
            <legend>{t('partnerLegend')}</legend>
            <p className="hint">{t('partnerHint')}</p>
            <Field id="pf-affiliate" label={t('partnerLink')} error={errors.affiliateUrl}>
              <input value={form.affiliateUrl} onChange={set('affiliateUrl')} maxLength={500} inputMode="url" spellCheck={false}
                     placeholder="https://www.amazon.com/dp/B0...?tag=your-tag-20" data-testid="pf-affiliate" />
            </Field>
          </fieldset>
          <div className="image-field">
            <div>
              <Field id="pf-image" label={t('picture')} error={errors.imageUrl} hint={t('pictureHint')}>
                <input value={form.imageUrl} onChange={set('imageUrl')} maxLength={500} inputMode="url" spellCheck={false}
                       placeholder="/products/my-product.svg" data-testid="pf-image" />
              </Field>
              {DEMO ? <p className="hint" data-testid="pf-photo-demo">{t('photoDemoOff')}</p> : (
                <div className="field photo-upload">
                  <label htmlFor="pf-photo">{photo.busy ? t('photoUploading') : t('photoUpload')}</label>
                  <input id="pf-photo" type="file" accept={ACCEPT} onChange={uploadPhoto} disabled={!!photo.busy}
                         aria-describedby="pf-photo-hint" data-testid="pf-photo" />
                  <span className="hint" id="pf-photo-hint">{t('photoHint')}</span>
                  <span aria-live="polite">
                    {photo.ok && <span className="ok" data-testid="pf-photo-ok">{photo.ok}</span>}
                    {photo.error && <span className="field-error" role="alert" data-testid="pf-photo-error">{photo.error}</span>}
                  </span>
                </div>
              )}
            </div>
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
                  <td data-testid="apr-name">{p.name}
                    {p.affiliateUrl && <span className="variant-tag partner-tag" data-testid="apr-partner">{partnerName(p.affiliateUrl)}</span>}
                    {p.variantGroup && <span className="variant-tag" data-testid="apr-variant">
                      {[p.color, p.size].filter(Boolean).join(' / ')}<span className="muted"> {p.variantGroup}</span></span>}
                  </td>
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
