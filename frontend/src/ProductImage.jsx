import React, { useEffect, useState } from 'react';

/** Category pictures for products without their own (or whose own picture cannot be loaded). */
const CATEGORY_PICTURES = { electronics: 'electronics', home: 'home', books: 'books', clothing: 'clothing' };

/** Same rule as the backend ProductRequest.imageUrl: the shop's own picture or an https:// link. */
export const IMAGE_URL = /^(\/products\/[a-z0-9][a-z0-9-]*\.(svg|png|jpg|webp)|https:\/\/[^\s"'<>]+)$/;

export function fallbackImage(category) {
  return `/products/${CATEGORY_PICTURES[(category || '').trim().toLowerCase()] ?? 'product'}.svg`;
}

/**
 * Product picture with a fallback: no imageUrl, or one that fails to load (deleted file, offline CDN),
 * shows the category picture instead, so a card never has a broken image icon.
 * alt="" where the product name is already next to the picture (cards), the name where it is not.
 */
export default function ProductImage({ product, alt = '', className = 'thumb', testId = 'product-image', eager = false }) {
  const fallback = fallbackImage(product.category);
  const wanted = product.imageUrl || fallback;
  const [src, setSrc] = useState(wanted);
  const [failed, setFailed] = useState(false);
  useEffect(() => { setSrc(wanted); setFailed(false); }, [wanted]);
  return (
    // `loading` comes before `src` on purpose: React writes the attributes in this order. The replacement must
    // already be eager when its address is set - WebKit did not always start a picture whose `src` changed while
    // it was still lazy, even when `loading` turned eager right after (DEF-026, seen again in CI on 16 Oct).
    <img className={className} loading={eager || failed ? 'eager' : 'lazy'} src={src} alt={alt} width="400" height="300"
         decoding="async" data-testid={testId} data-fallback={src === fallback}
         onError={() => { if (src !== fallback) { setFailed(true); setSrc(fallback); } }} />
  );
}
