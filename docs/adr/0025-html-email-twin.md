# ADR 0025: Every e-mail also goes out as HTML, built only from its plain text

- **Status:** Accepted
- **Date:** 2026-11 (before launch)

## Context
Order and account e-mails were plain text only (ADR-0014). They work everywhere, but next to other shops' mail they
look unfinished, links cannot be clicked in some clients, and the price breakdown loses its columns in proportional
fonts. A full template engine with separate HTML templates would double every e-mail and let the two drift apart.

## Decision
- **The plain text stays the single source.** `EmailHtml` turns it into HTML; both parts go out as
  `multipart/alternative` (SMTP: a MIME multipart, text first; Amazon SES: `Body.Text` and `Body.Html`). Clients pick
  one; filters, screen readers and old clients read the text.
- **Only the text becomes markup:** every character is escaped first, so a customer's own words (a contact message, a
  name) can never become HTML. Blank lines make paragraphs, line breaks `<br>`, `https://` links become links (no
  other scheme), and a block of price lines keeps its columns in a monospace box.
- **Layout:** one 600 px table with inline styles (what e-mail clients support), the store name (`app.store.name`) in
  the brand colour as the header, a short footer. **No images:** nothing is fetched when the mail is opened, so
  there is no tracking and no "load images?" banner; a logo can come later from the store's own domain.
- The outbox, retries and suppression list are unchanged: the HTML is made when a row is sent, not stored.

## Consequences
- Same words in both parts by construction; one place to change an e-mail.
- No per-e-mail designs (buttons, product pictures): enough for order and account mail; marketing mail would need more.
- Proven by `EmailHtmlTest`, `SesMailChannelTest` and `MailpitSmtpIntegrationTest` (both parts arrive) (REQ-NTF-08).
