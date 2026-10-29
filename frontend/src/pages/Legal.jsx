import React, { useEffect, useState } from 'react';
import api, { money } from '../api';
import { useI18n } from '../i18n';

/**
 * The store's policies: privacy, terms, returns, shipping (REQ-LEG-01..03).
 *
 * The numbers they quote (return window, shipping price, free-shipping threshold) and the business details come from
 * GET /api/store/legal, i.e. from the same settings the code runs on, so a policy cannot promise something the shop
 * does not do. Until the business is registered and a lawyer has read them, every page says it is a draft.
 * The texts are in English, the language of the business and of any legal review; the page says so in other languages.
 */
export const POLICIES = {
  privacy: { title: 'Privacy policy', nav: 'navPrivacy' },
  terms: { title: 'Terms of service', nav: 'navTerms' },
  'returns-policy': { title: 'Return and refund policy', nav: 'navReturnPolicy' },
  'shipping-policy': { title: 'Shipping policy', nav: 'navShippingPolicy' },
};

const PLACEHOLDER = {
  legalName: '[legal name of the business, e.g. "RbcTcsWorld LLC"]',
  address: '[business address]',
  contactEmail: '[contact e-mail address]',
};

function details(info) {
  const address = info.street && info.city && info.state && info.zip
    ? `${info.street}, ${info.city}, ${info.state} ${info.zip}` : PLACEHOLDER.address;
  return {
    store: info.storeName,
    who: info.legalName || PLACEHOLDER.legalName,
    address,
    email: info.contactEmail || PLACEHOLDER.contactEmail,
    days: info.returnWindowDays,
    fee: money(info.shippingFee),
    free: money(info.freeShippingThreshold),
    law: info.governingLaw,
  };
}

function Contact({ d }) {
  const mail = d.email.startsWith('[') ? <span data-testid="legal-contact">{d.email}</span>
    : <a href={`mailto:${d.email}`} data-testid="legal-contact">{d.email}</a>;
  return <p>{d.who}, {d.address}. E-mail: {mail}.</p>;
}

function Privacy({ d }) {
  return (
    <>
      <h2>Who we are</h2>
      <p>{d.store} is run by {d.who}, {d.address} ("we", "us"). This policy explains what we collect when you shop
        with us, why, who else sees it, and the choices you have.</p>

      <h2>What we collect</h2>
      <ul>
        <li><b>Your account:</b> your e-mail address and a password. We keep the password only as a one-way hash; nobody,
          including us, can read it.</li>
        <li><b>Delivery details:</b> name, address and an optional phone number, for each order and in your address book.</li>
        <li><b>Orders and returns:</b> what you bought, prices, tax, coupons, payment status, shipping and tracking, and
          return requests with the reason you give.</li>
        <li><b>Saved cards:</b> only the card brand, the last four digits, the expiry date and our payment processor's
          reference. We never store the full card number or the security code.</li>
        <li><b>Reviews and your wishlist,</b> if you write or keep them.</li>
        <li><b>Messages to us:</b> from the contact form, your name, e-mail address, the topic, an order number if you
          give one, your message and our answer.</li>
        <li><b>Security records:</b> failed sign-in attempts, sign-in sessions and, if you turn it on, two-factor sign-in.
          One-time sign-in codes and password-reset links are removed from our records once they are sent.</li>
        <li><b>Server logs:</b> the time and the requested address of visits, kept to keep the shop secure and working.</li>
      </ul>

      <h2>Why we use it</h2>
      <p>To take, pay for, ship and, if needed, return or refund your orders; to send you e-mails about your orders and
        your account; to answer your messages; to keep accounts and payments safe; and to keep the records that tax and accounting law requires.
        We do not sell your personal information, we do not share it for advertising, and we send no marketing e-mail.</p>

      <h2>Who else sees it</h2>
      <p>Only the companies that do part of the work for us, and only what that part needs:</p>
      <ul>
        <li><b>Stripe</b> processes card payments and calculates sales tax (it receives the order lines and the delivery
          address). When the secure card form is used, your card number goes straight to Stripe and never reaches our server.</li>
        <li><b>Shippo</b> and the carrier you are shipped with (USPS, UPS or FedEx) receive the name, address and phone
          number on the shipping label.</li>
        <li><b>Amazon Web Services (Amazon SES)</b> delivers our e-mails to you.</li>
        <li><b>Our hosting provider</b> runs the servers the shop and its database live on.</li>
      </ul>
      <p>We also disclose information when the law requires it, for example a court order.</p>

      <h2>Cookies and storage in your browser</h2>
      <p>The shop stores only what it needs to work in your browser's local storage: your sign-in (so you stay signed in)
        and the language you chose. We use no advertising or analytics cookies. When you pay with the secure card form,
        Stripe may set its own cookies to prevent fraud; see Stripe's privacy policy.</p>

      <h2>How long we keep it</h2>
      <p>Orders, payments and refunds: for as long as tax and accounting law requires. Your account, address book,
        saved cards, reviews and wishlist: until you ask us to delete them. Addresses that bounced or marked our e-mail
        as spam: kept so that we never e-mail them again.</p>

      <h2>Your choices</h2>
      <p>You can see and change your details, addresses and saved cards in your account at any time. To get a copy of your
        information, to correct it, or to have your account deleted, e-mail us from the address on your account. We
        answer within 45 days, and asking never changes the price or service you get. Records the law makes us keep
        (for example invoices) stay until that duty ends.</p>

      <h2>Children</h2>
      <p>The shop is not meant for children under 13, and we do not knowingly collect their information. If you think a
        child has given us information, contact us and we will delete it.</p>

      <h2>Security</h2>
      <p>The shop is served over HTTPS only, passwords are hashed, sign-ins are limited after repeated failures, and you can
        turn on two-factor sign-in. No system is perfectly secure; if a breach affects you, we will tell you as the law requires.</p>

      <h2>Changes and contact</h2>
      <p>If we change this policy, the new version shows a new effective date, and we e-mail you before an important change
        applies to information we already have. Questions or requests:</p>
      <Contact d={d} />
    </>
  );
}

function Terms({ d }) {
  return (
    <>
      <h2>Agreement</h2>
      <p>These terms apply when you use {d.store}, run by {d.who}, {d.address}. By creating an account or placing an order
        you agree to them, to our privacy policy, and to our return and shipping policies.</p>

      <h2>Your account</h2>
      <p>Give correct details and keep your password to yourself; you are responsible for what is done with your account.
        You can also check out as a guest with only an e-mail address. We may suspend an account that is used for fraud
        or to harm the shop or other customers.</p>

      <h2>Orders</h2>
      <p>Your order is an offer to buy. We confirm it by e-mail. We may refuse or cancel an order, for example when an item
        is no longer available or a price was clearly wrong; if you already paid, you get a full refund. You can cancel an
        order yourself until it ships, with a full refund of anything paid.</p>

      <h2>Prices, tax and coupons</h2>
      <p>Prices are in US dollars. Sales tax is calculated for your delivery address and shown before you pay. A coupon
        gives the discount shown at checkout under its own conditions (dates, minimum order, number of uses); it has no
        cash value and only one coupon applies per order.</p>

      <h2>Payment</h2>
      <p>Card payments are processed by Stripe. Your order is paid when the payment succeeds; a declined card leaves the
        order unpaid and you can try another card.</p>

      <h2>Shipping and returns</h2>
      <p>Orders ship to addresses in the United States. Shipping costs and times are in the shipping policy; returns and
        refunds in the return and refund policy.</p>

      <h2>Reviews</h2>
      <p>Reviews must be about the product and honest. You keep the rights to what you write and allow us to show it in the
        shop. We may hide reviews that are offensive, off-topic or misleading.</p>

      <h2>Use of the shop</h2>
      <p>Do not try to break into the shop, overload it, copy its catalog by automated means, or use it for anything unlawful.</p>

      <h2>Liability</h2>
      <p>We do our best to keep the shop available and the information in it correct, but we cannot promise it is always
        free of errors or interruptions. To the extent the law allows, our liability for an order is limited to the amount
        you paid for it. Nothing in these terms limits rights that consumer law gives you.</p>

      <h2>Law, changes and contact</h2>
      <p>These terms are governed by the laws of {d.law} and of the United States. If we change them, the new version
        applies to orders placed after its effective date. Questions:</p>
      <Contact d={d} />
    </>
  );
}

function Returns({ d }) {
  return (
    <>
      <h2>Changed your mind before it ships</h2>
      <p>Cancel the order from your order page while it is still placed or paid. Anything you paid is refunded in full.</p>

      <h2>Returning a delivered order</h2>
      <p>You can ask for a return within <b data-testid="legal-return-days">{d.days} days</b> after the order was delivered,
        from your order page, once per order, with the reason. We look at every request and tell you by e-mail whether it
        is approved, with instructions for sending the items back.</p>

      <h2>What you get back</h2>
      <table className="legal-table">
        <thead><tr><th scope="col">Reason</th><th scope="col">Refund</th></tr></thead>
        <tbody>
          <tr><td>The item arrived damaged</td><td>Everything you paid, shipping included</td></tr>
          <tr><td>We sent the wrong item</td><td>Everything you paid, shipping included</td></tr>
          <tr><td>The item is not as described</td><td>Everything you paid, shipping included</td></tr>
          <tr><td>You no longer need it</td><td>Everything you paid except the shipping fee</td></tr>
        </tbody>
      </table>
      <p>Refunds go back to the card you paid with, as soon as the return is approved. Your bank may take a few business
        days to show it.</p>

      <h2>Questions</h2>
      <Contact d={d} />
    </>
  );
}

function Shipping({ d }) {
  return (
    <>
      <h2>Where we ship</h2>
      <p>To addresses in the 50 United States and the District of Columbia.</p>

      <h2>What it costs</h2>
      <p>Shipping is <b data-testid="legal-shipping-fee">{d.fee}</b> per order, and free for orders of
        <b data-testid="legal-free-shipping"> {d.free}</b> or more after discounts. The exact amount is shown at checkout
        before you pay.</p>

      <h2>How it ships</h2>
      <p>Orders are packed after payment and sent with USPS, UPS or FedEx. When your order ships you get an e-mail with the
        carrier and the tracking number, and the order page shows its progress. We e-mail you again when the carrier
        reports it delivered.</p>

      <h2>A problem with a delivery</h2>
      <p>If a parcel is late, lost or arrives damaged, contact us with your order number. Damaged items are covered by the
        return and refund policy.</p>
      <Contact d={d} />
    </>
  );
}

/** 2026-10-28 -> October 28, 2026 (the date itself, whatever the viewer's time zone). */
export function longDate(iso) {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d)).toLocaleDateString('en-US', { dateStyle: 'long', timeZone: 'UTC' });
}

const BODY = { privacy: Privacy, terms: Terms, 'returns-policy': Returns, 'shipping-policy': Shipping };

export default function Legal({ page }) {
  const { t, lang } = useI18n();
  const [info, setInfo] = useState(null);
  const [error, setError] = useState(false);
  useEffect(() => { api.get('/store/legal').then(r => setInfo(r.data)).catch(() => setError(true)); }, []);

  const policy = POLICIES[page];
  const Body = BODY[page];
  return (
    <article className="card legal" data-testid="legal-page" data-policy={page}>
      <h1 data-testid="legal-title">{policy.title}</h1>
      {lang !== 'en' && <p className="muted" data-testid="legal-english">{t('legalInEnglish')}</p>}
      {error && <p className="error" role="alert">{t('legalUnavailable')}</p>}
      {info && (
        <>
          <p className="muted" data-testid="legal-effective">Effective {longDate(info.effectiveDate)}</p>
          {!info.reviewed && (
            <p className="notice legal-draft" role="note" data-testid="legal-draft">
              <b>Draft.</b> This policy describes how the shop works today. It has not yet been reviewed by a lawyer
              {!info.complete && ', and the business details are not filled in yet'}.
            </p>
          )}
          <Body d={details(info)} />
        </>
      )}
      <nav className="legal-links" aria-label={t('secLegal')}>
        {Object.entries(POLICIES).filter(([k]) => k !== page).map(([k, p]) => (
          <a key={k} href={`#/${k}`}>{t(p.nav)}</a>
        ))}
      </nav>
    </article>
  );
}
