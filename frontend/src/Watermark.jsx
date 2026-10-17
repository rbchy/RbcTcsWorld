import React from 'react';

/** One tile of the watermark: the name twice, on a slant (repeated by CSS as a background). */
function tile(fill) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="320" height="96">`
    + `<g transform="rotate(-18 160 48)" font-family="system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif" `
    + `font-size="22" font-weight="800" letter-spacing="0.9" fill="${fill}">`
    + `<text x="0" y="34">RbcTcsWorld</text><text x="160" y="82">RbcTcsWorld</text></g></svg>`;
  return `url("data:image/svg+xml,${encodeURIComponent(svg)}")`;
}

const TONES = {
  dark: tile('rgba(232, 241, 243, 0.07)'),     // on the harbor-blue banners
  light: tile('rgba(18, 49, 63, 0.06)'),       // on white (report sheets)
};

/**
 * The RbcTcsWorld watermark behind a banner. It is a background picture, not text: screen readers,
 * copy-paste and the colour-contrast checks never see it.
 */
export default function Watermark({ tone = 'light' }) {
  return <div className={`watermark ${tone}`} aria-hidden="true" data-testid="watermark"
              style={{ backgroundImage: TONES[tone] || TONES.light }} />;
}
