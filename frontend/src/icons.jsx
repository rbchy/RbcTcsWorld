import React from 'react';

/**
 * The storefront's icon set: 24x24 line drawings, stroke = currentColor, so an icon takes the colour of the text
 * beside it. Icons are decorative (aria-hidden): every button and link keeps a text name for screen readers and
 * for the tests.
 */
export const ICONS = {
  bag: 'M6 7h12l1 13H5L6 7zM9 7a3 3 0 0 1 6 0',
  heart: 'M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z',
  cart: 'M3 4h2l2.4 11h10.2L20 8H6.2M10.5 20a1.5 1.5 0 1 1-3 0a1.5 1.5 0 1 1 3 0M18.5 20a1.5 1.5 0 1 1-3 0a1.5 1.5 0 1 1 3 0',
  box: 'M3 7l9-4 9 4v10l-9 4-9-4V7zM3 7l9 4 9-4M12 11v10',
  receipt: 'M6 3h12v18l-3-2-3 2-3-2-3 2V3zM9 8h6M9 12h6M9 16h3',
  login: 'M14 4h5a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-5M10 8l4 4-4 4M14 12H4',
  logout: 'M10 4H5a1 1 0 0 0-1 1v14a1 1 0 0 0 1 1h5M15 8l4 4-4 4M19 12H9',
  exit: 'M12 3v8M7.1 6.4a7 7 0 1 0 9.8 0',
  userPlus: 'M13 7a4 4 0 1 1-8 0a4 4 0 1 1 8 0M2 21a7 7 0 0 1 14 0M19 8v6M16 11h6',
  user: 'M16 7a4 4 0 1 1-8 0a4 4 0 1 1 8 0M4 21a8 8 0 0 1 16 0',
  users: 'M13 7a4 4 0 1 1-8 0a4 4 0 1 1 8 0M2 21a7 7 0 0 1 14 0M16 3.5a4 4 0 0 1 0 7.5M18 14a6 6 0 0 1 4 7',
  help: 'M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0M9.5 9a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .9-1 1.6V14M12 17.5v.01',
  grid: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',
  chart: 'M4 20V4M4 20h16M8 16v-5M12 16V8M16 16v-3',
  tag: 'M3 12V4h8l10 10-8 8L3 12zM7.5 7.5v.01',
  ticket: 'M3 6h18v4a2 2 0 0 0 0 4v4H3v-4a2 2 0 0 0 0-4V6zM14 6v2M14 11v2M14 16v2',
  warehouse: 'M3 21V9l9-5 9 5v12M7 21v-8h10v8M7 17h10',
  card: 'M3 6h18v12H3zM3 10h18M7 15h3',
  returnArrow: 'M9 14L4 9l5-5M4 9h11a5 5 0 0 1 0 10h-3',
  star: 'M12 3l2.8 5.7 6.2.9-4.5 4.4 1 6.2L12 17.3l-5.5 2.9 1-6.2L3 9.6l6.2-.9L12 3z',
  bell: 'M6 16v-5a6 6 0 0 1 12 0v5l2 2H4l2-2zM10 21h4',
  shield: 'M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6l8-3zM9 12l2 2 4-4',
  search: 'M18 11a7 7 0 1 1-14 0a7 7 0 1 1 14 0M20 20l-4-4',
  reset: 'M4 12a8 8 0 1 0 2.3-5.7M4 4v4h4',
  download: 'M12 4v11M7 10l5 5 5-5M5 20h14',
  check: 'M5 12l4 4 10-10',
  x: 'M6 6l12 12M18 6L6 18',
  trash: 'M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v6M14 11v6',
  edit: 'M4 20h4L19 9l-4-4L4 16v4zM13 7l4 4',
  plus: 'M12 5v14M5 12h14',
  calendar: 'M4 6h16v14H4zM4 10h16M8 3v5M16 3v5',
  chevronLeft: 'M15 6l-6 6 6 6',
  chevronRight: 'M9 6l6 6-6 6',
  chevronDown: 'M6 9l6 6 6-6',
  print: 'M7 9V3h10v6M7 17H4v-7h16v7h-3M7 14h10v7H7z',
  mail: 'M3 6h18v12H3zM3 7l9 6 9-6',
  pin: 'M12 21s-7-6-7-11a7 7 0 0 1 14 0c0 5-7 11-7 11zM14 10a2 2 0 1 1-4 0a2 2 0 1 1 4 0',
  lock: 'M6 11h12v10H6zM8 11V7a4 4 0 0 1 8 0v4',
  up: 'M12 19V5M6 11l6-6 6 6',
  truck: 'M3 6h11v10H3zM14 9h4l3 3v4h-7M9 18a2 2 0 1 1-4 0a2 2 0 1 1 4 0M19 18a2 2 0 1 1-4 0a2 2 0 1 1 4 0',
  eye: 'M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12zM15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0',
  clock: 'M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0M12 7v5l3 2',
  transfer: 'M4 8h15l-4-4M20 16H5l4 4',
  sliders: 'M4 6h16M4 12h16M4 18h16M9 4v4M15 10v4M7 16v4',
  power: 'M12 3v8M7.1 6.4a7 7 0 1 0 9.8 0',
  phone: 'M7 3h10v18H7zM11 18h2',
  alert: 'M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0M12 7v6M12 16.5v.01',
  globe: 'M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0M3 12h18M12 3c2.5 2.6 3.8 5.6 3.8 9s-1.3 6.4-3.8 9c-2.5-2.6-3.8-5.6-3.8-9S9.5 5.6 12 3',
};

export function Icon({ name, size = 18, className = '' }) {
  return (
    <svg className={`icon ${className}`.trim()} viewBox="0 0 24 24" width={size} height={size} aria-hidden="true" focusable="false">
      <path d={ICONS[name]} fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

/** The same drawing as a CSS mask, so a plain button can carry it in ::before (see BUTTON_ICONS). */
function maskUrl(name) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="${ICONS[name]}" fill="none" stroke="#000" `
    + 'stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
  return `url("data:image/svg+xml,${encodeURIComponent(svg)}")`;
}

/**
 * Which buttons get which icon, chosen by their data-testid. One rule here gives every Search, Reset, Export,
 * Save, Cancel, Delete, Edit ... button in the store the same picture, without touching each page. Later rows win.
 */
const b = s => `button[data-testid${s}]`;
export const BUTTON_ICONS = [
  ['search', [b('$="-search"'), b('="ss-search"')]],
  ['reset', [b('$="-reset"'), b('="anf-retry"')]],
  ['download', [b('$="-export"'), b('="install-app"')]],
  ['check', [b('$="-save"'), b('="review-submit"'), b('="return-submit"'), b('="place-order"'), b('="arr-approve"'),
    b('="arv-publish"'), b('="aor-deliver"'), b('="customer-enable"'), b('="customer-disable-yes"')]],
  ['x', [b('$="-cancel"'), b('="cancel-order"'), b('="customer-close"'), b('="arr-reject"'), b('="arv-hide"'),
    b('="customer-disable"'), b('="customer-disable-no"')]],
  ['trash', [b('="line-remove"'), b('="wl-remove"'), b('="apr-delete"'), b('="apr-delete-yes"')]],
  ['edit', [b('$="-edit"')]],
  ['cart', [b('="add-to-cart"'), b('="wl-move"')]],
  ['card', [b('="pay-button"'), b('="go-checkout"')]],
  ['heart', [b('="detail-wishlist"')]],
  ['chart', [b('="report-run"')]],
  ['calendar', [b('^="report-last-"')]],
  ['print', [b('="report-print"')]],
  ['shield', [b('="audit-verify"')]],
  ['eye', [b('="audit-show"'), b('="customer-view"')]],
  ['clock', [b('="apr-history"')]],
  ['plus', [b('="admin-product-add"'), b('="warehouse-add"')]],
  ['truck', [b('="aor-ship"')]],
  ['ticket', [b('="coupon-apply"')]],
  ['power', [b('="coupon-toggle"')]],
  ['lock', [b('="auth-submit"'), b('="reset-submit"')]],
  ['mail', [b('="forgot-submit"')]],
  ['transfer', [b('="tr-save"')]],
  ['sliders', [b('="adj-save"')]],
  ['chevronDown', [b('="load-more"')]],
  ['chevronLeft', [b('$="-prev"')]],
  ['trash', [b('="address-delete"'), b('="address-delete-yes"')]],
  ['x', [b('="address-delete-no"')]],
  ['star', [b('="address-make-default"')]],
  ['bag', [b('="guest-continue"')]],
  ['cart', [b('="detail-add-to-cart"')]],
  ['chevronRight', [b('$="-next"')]],
  // not buttons: the same icons in front of messages
  ['alert', ['.field-error', 'p.error', '.error-text']],
  ['check', ['p.ok']],
];

/** Puts the button (and message) icons into the page once (a <style> element; the CSP allows inline styles). */
export function installButtonIcons(doc = document) {
  if (doc.getElementById('button-icons')) return;
  const all = BUTTON_ICONS.flatMap(([, sel]) => sel.map(s => `${s}::before`)).join(',\n');
  const css = [`${all} { content: ""; display: inline-block; flex: none; width: 1.05em; height: 1.05em; vertical-align: -0.17em;
    margin-inline-end: 0.45em; background-color: currentColor; -webkit-mask: var(--btn-icon) center / contain no-repeat;
    mask: var(--btn-icon) center / contain no-repeat; }`]
    .concat(BUTTON_ICONS.map(([name, sel]) => `${sel.join(', ')} { --btn-icon: ${maskUrl(name)}; }`));
  const style = doc.createElement('style');
  style.id = 'button-icons';
  style.textContent = css.join('\n');
  doc.head.appendChild(style);
}
