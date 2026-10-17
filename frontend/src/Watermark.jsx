import React, { useId } from 'react';

/**
 * The RbcTcsWorld watermark: the name repeated on a slant, very faint, behind a banner.
 * Purely decorative (aria-hidden), so screen readers and the axe tests ignore it.
 */
export default function Watermark({ tone = 'light' }) {
  const id = `wm-${useId().replace(/:/g, '')}`;
  return (
    <svg className={`watermark ${tone}`} aria-hidden="true" focusable="false" data-testid="watermark">
      <defs>
        <pattern id={id} width="320" height="96" patternUnits="userSpaceOnUse" patternTransform="rotate(-18)">
          <text x="0" y="34">RbcTcsWorld</text>
          <text x="160" y="82">RbcTcsWorld</text>
        </pattern>
      </defs>
      <rect width="100%" height="100%" fill={`url(#${id})`} />
    </svg>
  );
}
