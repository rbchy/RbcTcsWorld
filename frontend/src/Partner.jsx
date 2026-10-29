import React from 'react';
import { useI18n } from './i18n';
import { Icon } from './icons';

/**
 * Affiliate (partner) products, ADR-0020: bought at the partner shop through their link, never in our cart.
 *  - No price is shown: Amazon allows prices only from its own API and at most 24 hours old.
 *  - The link is rel="sponsored": search engines must not count paid links as recommendations.
 *  - The disclosure sits right above the button, as the FTC and Amazon ask (a footer line alone is not enough).
 */
export function partnerHost(url) {
  try { return new URL(url).hostname.replace(/^www\./, ''); } catch { return ''; }
}

export const isAmazon = url => /(^|\.)amazon\.[a-z.]+$/.test(partnerHost(url)) || partnerHost(url) === 'amzn.to';

export function partnerName(url) {
  return isAmazon(url) ? 'Amazon' : partnerHost(url);
}

export function PartnerButton({ product, testId = 'partner-link', className = '' }) {
  const { t } = useI18n();
  return (
    <a className={`button-link choose partner-button ${className}`.trim()} href={product.affiliateUrl} target="_blank"
       rel="sponsored nofollow noopener noreferrer" data-testid={testId}>
      {t('buyAt', { shop: partnerName(product.affiliateUrl) })} <Icon name="transfer" size={16} />
    </a>
  );
}

/** The "we earn from qualifying purchases" line: Amazon's own wording when an Amazon link is on the page. */
export function PartnerDisclosure({ urls, testId = 'partner-disclosure' }) {
  const { t } = useI18n();
  const links = urls.filter(Boolean);
  if (!links.length) return null;
  return (
    <p className="partner-disclosure" role="note" data-testid={testId}>
      {links.some(isAmazon) && <>{t('amazonDisclosure')} </>}
      {t('partnerDisclosure')}
    </p>
  );
}
