import React from 'react';

/**
 * The storefront banner picture: the RbcTcsWorld shop front with a striped awning, goods in the window, an open
 * door, parcels ready to go out and a -10% badge for the welcome offer. Drawn in the shop's own colours
 * (midnight indigo, violet, coral, sunflower). Purely decorative, so screen readers skip it.
 */
export default function ShopScene() {
  return (
    <svg className="shop-scene" viewBox="0 0 440 290" direction="ltr" aria-hidden="true" focusable="false" data-testid="banner-art">
      {/* ground */}
      <ellipse cx="222" cy="270" rx="200" ry="13" fill="#4f47b8" opacity="0.16" />

      {/* building */}
      <rect x="92" y="58" width="260" height="206" rx="6" fill="#fff" stroke="#d8d1f0" strokeWidth="2" />
      <rect x="92" y="58" width="260" height="34" rx="6" fill="#120c36" />
      <rect x="148" y="66" width="148" height="18" rx="9" fill="#ffd35c" />
      <text x="222" y="80" textAnchor="middle" fontSize="13" fontWeight="800" letterSpacing="2" fill="#1e1650"
            fontFamily="system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif">SHOP</text>

      {/* awning: coral and white stripes with a scalloped edge */}
      <g>
        {[0, 1, 2, 3, 4, 5, 6, 7, 8, 9].map(i => (
          <rect key={i} x={84 + i * 27.6} y="92" width="27.6" height="30" fill={i % 2 ? '#fff' : '#d9365e'} />
        ))}
        {[0, 1, 2, 3, 4, 5, 6, 7, 8, 9].map(i => (
          <path key={`s${i}`} d={`M${84 + i * 27.6} 122 a13.8 11 0 0 0 27.6 0 z`} fill={i % 2 ? '#fff' : '#d9365e'} />
        ))}
        <rect x="84" y="88" width="276" height="6" rx="3" fill="#120c36" />
      </g>

      {/* shop window with shelves of goods */}
      <rect x="110" y="146" width="132" height="96" rx="6" fill="#1e1650" />
      <rect x="116" y="152" width="120" height="84" rx="4" fill="#ece8fb" />
      <rect x="116" y="192" width="120" height="5" fill="#b9addf" />
      <rect x="116" y="231" width="120" height="5" fill="#b9addf" />
      {/* top shelf: a mug, a book, a box */}
      <rect x="126" y="170" width="18" height="22" rx="3" fill="#d9365e" />
      <path d="M144 175 h4 a5 5 0 0 1 0 10 h-4" fill="none" stroke="#d9365e" strokeWidth="3" />
      <rect x="160" y="164" width="16" height="28" rx="2" fill="#5b2fd6" />
      <rect x="179" y="168" width="14" height="24" rx="2" fill="#ffd35c" />
      <rect x="203" y="172" width="24" height="20" rx="2" fill="#1e1650" />
      <rect x="213" y="172" width="4" height="20" fill="#ffd35c" />
      {/* bottom shelf: a t-shirt and a bottle */}
      <path d="M128 208 l8 -6 h12 l8 6 l-4 7 h-3 v16 h-14 v-16 h-3 z" fill="#5b2fd6" />
      <rect x="176" y="206" width="12" height="25" rx="4" fill="#1e1650" />
      <rect x="179" y="201" width="6" height="6" rx="1" fill="#1e1650" />
      <circle cx="210" cy="221" r="10" fill="#ffd35c" />
      <path d="M205 221 l4 4 l7 -8" fill="none" stroke="#1e1650" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" />

      {/* door, open sign */}
      <rect x="260" y="146" width="74" height="118" rx="5" fill="#5b2fd6" />
      <rect x="268" y="154" width="58" height="56" rx="4" fill="#ece8fb" opacity="0.92" />
      <circle cx="318" cy="234" r="4" fill="#ffd35c" />
      <path d="M284 154 l13 14 l13 -14" fill="none" stroke="#120c36" strokeWidth="2" />
      <rect x="276" y="168" width="42" height="22" rx="4" fill="#d9365e" />
      <text x="297" y="183" textAnchor="middle" fontSize="11" fontWeight="800" fill="#fff"
            fontFamily="system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif">OPEN</text>

      {/* parcels ready to go */}
      <g>
        <rect x="30" y="214" width="54" height="50" rx="4" fill="#ffd35c" />
        <rect x="53" y="214" width="8" height="50" fill="#e0b23d" />
        <rect x="40" y="178" width="38" height="36" rx="4" fill="#fff" stroke="#d8d1f0" strokeWidth="2" />
        <rect x="55" y="178" width="7" height="36" fill="#d8d1f0" />
        <rect x="364" y="226" width="44" height="38" rx="4" fill="#fff" stroke="#d8d1f0" strokeWidth="2" />
        <rect x="382" y="226" width="7" height="38" fill="#d9365e" />
      </g>

      {/* -10% badge for the welcome offer */}
      <g transform="rotate(12 386 74)">
        <circle cx="386" cy="74" r="34" fill="#d9365e" />
        <circle cx="386" cy="74" r="28" fill="none" stroke="#fff" strokeWidth="2" strokeDasharray="4 4" />
        <text x="386" y="82" textAnchor="middle" fontSize="21" fontWeight="900" fill="#fff"
              fontFamily="system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif">-10%</text>
      </g>

      {/* sparkles */}
      <path d="M52 92 l4 10 l10 4 l-10 4 l-4 10 l-4 -10 l-10 -4 l10 -4 z" fill="#ffb020" />
      <path d="M408 160 l3 7 l7 3 l-7 3 l-3 7 l-3 -7 l-7 -3 l7 -3 z" fill="#ffb020" opacity="0.85" />
      <circle cx="78" cy="140" r="3.5" fill="#ffd35c" opacity="0.8" />
      <circle cx="420" cy="214" r="3" fill="#ffd35c" />
    </svg>
  );
}
