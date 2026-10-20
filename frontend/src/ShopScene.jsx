import React from 'react';

/**
 * The storefront banner picture: a phone with the shop open, a cart full of parcels, a shopping bag,
 * a payment card and a price tag. Drawn in the shop's own colours (harbor blue, label cream, accent amber)
 * so it matches the product pictures. Purely decorative, so screen readers skip it.
 */
export default function ShopScene() {
  return (
    <svg className="shop-scene" viewBox="0 0 440 280" direction="ltr" aria-hidden="true" focusable="false" data-testid="banner-art">
      {/* soft ground shadow */}
      <ellipse cx="226" cy="258" rx="196" ry="14" fill="#0b2029" opacity="0.45" />

      {/* phone with the shop open */}
      <g transform="rotate(-6 196 140)">
        <rect x="134" y="22" width="132" height="232" rx="20" fill="#0b2029" />
        <rect x="142" y="34" width="116" height="208" rx="13" fill="#fffdf6" />
        <rect x="186" y="27" width="28" height="4" rx="2" fill="#2e5664" />
        <rect x="152" y="44" width="58" height="8" rx="4" fill="#12313f" />
        <circle cx="244" cy="48" r="6" fill="#b45309" />
        <rect x="152" y="60" width="96" height="44" rx="8" fill="#f4d58d" />
        <text x="160" y="80" fontSize="12" fontWeight="800" fill="#12313f">-10%</text>
        <rect x="160" y="88" width="44" height="6" rx="3" fill="#12313f" opacity="0.5" />
        <rect x="152" y="112" width="45" height="52" rx="7" fill="#dbe7ea" />
        <rect x="203" y="112" width="45" height="52" rx="7" fill="#dbe7ea" />
        <circle cx="174" cy="132" r="11" fill="#1d4555" />
        <rect x="214" y="122" width="23" height="18" rx="3" fill="#b45309" />
        <rect x="157" y="152" width="22" height="5" rx="2.5" fill="#12313f" />
        <rect x="208" y="152" width="22" height="5" rx="2.5" fill="#12313f" />
        <rect x="152" y="172" width="45" height="52" rx="7" fill="#dbe7ea" />
        <rect x="203" y="172" width="45" height="52" rx="7" fill="#dbe7ea" />
        <path d="M164 204 l10 -14 l10 14 z" fill="#1d4555" />
        <circle cx="225" cy="192" r="10" fill="#f4d58d" />
        <rect x="157" y="212" width="22" height="5" rx="2.5" fill="#12313f" />
        <rect x="208" y="212" width="22" height="5" rx="2.5" fill="#12313f" />
        <rect x="152" y="230" width="96" height="6" rx="3" fill="#b45309" opacity="0.85" />
      </g>

      {/* payment card */}
      <g transform="rotate(-14 74 70)">
        <rect x="22" y="40" width="112" height="70" rx="10" fill="#1d4555" />
        <rect x="22" y="56" width="112" height="12" fill="#0b2029" />
        <rect x="34" y="78" width="22" height="16" rx="3" fill="#f4d58d" />
        <rect x="64" y="84" width="54" height="6" rx="3" fill="#e8f1f3" opacity="0.7" />
      </g>

      {/* shopping bag */}
      <g>
        <path d="M58 150 q0 -26 22 -26 q22 0 22 26" fill="none" stroke="#fffdf6" strokeWidth="6" strokeLinecap="round" />
        <path d="M40 148 h80 l-8 104 h-64 z" fill="#b45309" />
        <path d="M40 148 h80 l-2 22 h-76 z" fill="#8f4207" />
        <circle cx="80" cy="206" r="15" fill="#fffdf6" />
        <path d="M74 206 l5 5 l9 -11" fill="none" stroke="#b45309" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
      </g>

      {/* cart full of parcels */}
      <g>
        <rect x="318" y="118" width="40" height="36" rx="3" fill="#fffdf6" />
        <rect x="335" y="118" width="7" height="36" fill="#c7b98f" />
        <rect x="352" y="104" width="44" height="50" rx="3" fill="#f4d58d" />
        <rect x="370" y="104" width="8" height="50" fill="#c79a3a" />
        <rect x="300" y="132" width="26" height="22" rx="3" fill="#dbe7ea" />
        <path d="M268 112 h22 l10 46 h104 l12 -42" fill="none" stroke="#e8f1f3" strokeWidth="7" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M296 158 h108 l-6 26 h-96 z" fill="#e8f1f3" />
        <path d="M304 196 h92" stroke="#e8f1f3" strokeWidth="6" strokeLinecap="round" />
        <circle cx="314" cy="230" r="14" fill="#0b2029" /><circle cx="314" cy="230" r="6" fill="#e8f1f3" />
        <circle cx="386" cy="230" r="14" fill="#0b2029" /><circle cx="386" cy="230" r="6" fill="#e8f1f3" />
        <path d="M306 196 l8 20 M394 196 l-8 20" stroke="#e8f1f3" strokeWidth="6" strokeLinecap="round" />
      </g>

      {/* price tag */}
      <g transform="rotate(16 380 46)">
        <path d="M340 30 h62 l16 18 l-16 18 h-62 z" fill="#fffdf6" />
        <circle cx="402" cy="48" r="4" fill="#12313f" />
        <text x="350" y="54" fontSize="17" fontWeight="800" fill="#b45309">SALE</text>
      </g>

      {/* sparkles */}
      <path d="M300 64 l4 10 l10 4 l-10 4 l-4 10 l-4 -10 l-10 -4 l10 -4 z" fill="#f4d58d" />
      <path d="M128 18 l3 7 l7 3 l-7 3 l-3 7 l-3 -7 l-7 -3 l7 -3 z" fill="#f4d58d" opacity="0.8" />
      <circle cx="292" cy="248" r="3" fill="#f4d58d" />
      <circle cx="26" cy="168" r="4" fill="#f4d58d" opacity="0.7" />
    </svg>
  );
}
