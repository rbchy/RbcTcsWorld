import React from 'react';

const FONT = 'Plus Jakarta Sans, system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif';

/**
 * The storefront banner picture: an RbcTcsWorld van on a winding road to a customer's home, a map pin over the
 * house, a parcel at the door and "free over $50" on the way: everyday goods, delivered to your door.
 * It fills the right half of the banner (slice keeps the sky and hills edge to edge at any banner size).
 * Purely decorative, so screen readers skip it.
 */
export default function ShopScene() {
  return (
    <svg className="shop-scene" viewBox="0 0 460 300" preserveAspectRatio="xMidYMid slice" direction="ltr"
         aria-hidden="true" focusable="false" data-testid="banner-art">
      {/* sun and clouds */}
      <circle cx="372" cy="70" r="46" fill="#ffd35c" opacity="0.22" />
      <circle cx="372" cy="70" r="30" fill="#ffd35c" />
      <g fill="#fff">
        <ellipse cx="96" cy="62" rx="34" ry="13" /><ellipse cx="120" cy="54" rx="22" ry="13" />
        <ellipse cx="250" cy="42" rx="26" ry="10" /><ellipse cx="268" cy="36" rx="16" ry="10" />
      </g>

      {/* hills */}
      <path d="M-40 206 q140 -64 270 -12 t270 -22 v140 h-540 z" fill="#d9f0e4" />
      <path d="M-40 236 q160 -44 290 0 t250 -10 v90 h-540 z" fill="#c4e7d5" />

      {/* road */}
      <path d="M-30 300 C 80 258, 160 272, 240 240 S 360 202, 400 198" fill="none" stroke="#b9c3d6" strokeWidth="32" strokeLinecap="round" />
      <path d="M-30 300 C 80 258, 160 272, 240 240 S 360 202, 400 198" fill="none" stroke="#fff" strokeWidth="3" strokeDasharray="12 12" />

      {/* home */}
      <g>
        <rect x="352" y="150" width="76" height="52" rx="3" fill="#fff" />
        <path d="M344 154 l46 -36 l46 36 z" fill="#d9365e" />
        <rect x="380" y="172" width="20" height="30" rx="2" fill="#4f47b8" />
        <rect x="362" y="164" width="14" height="14" rx="2" fill="#bfe0ff" />
        <rect x="406" y="164" width="14" height="14" rx="2" fill="#bfe0ff" />
      </g>
      <rect x="330" y="186" width="24" height="18" rx="2" fill="#ffd35c" />
      <rect x="340" y="186" width="4" height="18" fill="#e0b23d" />

      {/* map pin over the home, and the route to it */}
      <path d="M390 54 a22 22 0 0 1 22 22 c0 18 -22 38 -22 38 s-22 -20 -22 -38 a22 22 0 0 1 22 -22 z" fill="#5b2fd6" />
      <circle cx="390" cy="76" r="8" fill="#fff" />
      <path d="M390 116 q-40 30 -120 52" fill="none" stroke="#5b2fd6" strokeWidth="2" strokeDasharray="4 6" opacity="0.6" />

      {/* the van */}
      <g transform="translate(120 170) rotate(-8)">
        <rect x="0" y="0" width="112" height="58" rx="8" fill="#5b2fd6" />
        <path d="M112 14 h26 l18 22 v22 h-44 z" fill="#4f47b8" />
        <path d="M118 20 h18 l12 16 h-30 z" fill="#d9ecff" />
        <rect x="8" y="10" width="92" height="22" rx="5" fill="#fff" />
        <text x="54" y="26" textAnchor="middle" fontSize="12" fontWeight="800" fill="#4f47b8" fontFamily={FONT}>RbcTcsWorld</text>
        <rect x="12" y="38" width="22" height="12" rx="2" fill="#ffd35c" />
        <rect x="38" y="38" width="22" height="12" rx="2" fill="#ffd35c" opacity="0.8" />
        <circle cx="30" cy="60" r="13" fill="#1e1650" /><circle cx="30" cy="60" r="5" fill="#e8e5f5" />
        <circle cx="128" cy="60" r="13" fill="#1e1650" /><circle cx="128" cy="60" r="5" fill="#e8e5f5" />
        <path d="M-36 14 h24 M-46 28 h30 M-30 42 h18" stroke="#9aa6c4" strokeWidth="4" strokeLinecap="round" />
      </g>

      {/* free shipping tag */}
      <g transform="rotate(8 70 120)">
        <rect x="40" y="104" width="96" height="30" rx="15" fill="#fff" />
        <text x="88" y="124" textAnchor="middle" fontSize="13" fontWeight="800" fill="#1d6b3a" fontFamily={FONT}>Free over $50</text>
      </g>
    </svg>
  );
}
