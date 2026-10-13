import React, { useEffect, useState } from 'react';

/** Category pictures for products without their own (or whose own picture cannot be loaded). */
const CATEGORY_PICTURES = { electronics: 'electronics', home: 'home', books: 'books' };

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
  useEffect(() => setSrc(wanted), [wanted]);
  return (
    <img className={className} src={src} alt={alt} width="400" height="300" loading={eager ? 'eager' : 'lazy'}
         decoding="async" data-testid={testId} data-fallback={src === fallback}
         onError={() => { if (src !== fallback) setSrc(fallback); }} />
  );
}
