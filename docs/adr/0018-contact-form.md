# ADR 0018: A contact form answered from the admin page, with e-mail through the outbox and no CAPTCHA

- **Status:** Accepted
- **Date:** 2026-10 (the "Customer service" row of the roadmap)

## Context
A live store must give customers a way to ask about an order, a return or a product, and the owner must see every
question and be able to prove what was answered. A bare `mailto:` link needs the store's mailbox to exist, puts every
question in the owner's personal inbox with no status, and exposes the address to spam harvesters. A public form,
on the other hand, is the first thing bots find: it gets spam, and if it e-mails what was typed to the address that
was typed, it becomes a free spam relay with the store's domain as sender.

## Decision
- **`POST /api/contact`, public:** name, e-mail, topic (ORDER, RETURN, PRODUCT, PAYMENT, OTHER), optional order
  number, message (10-4000 characters). The answer is a ticket (`MSG-` + 6 characters without 0/O, 1/I/L) and the
  promised answer time (`STORE_REPLY_DAYS`, default 2 business days).
- **Logged in = the account's address:** a customer who is signed in writes with the account's own e-mail; the address
  in the request is ignored. Nobody can write in someone else's name from an account.
- **Two e-mails, both through the outbox** (ADR-0005), in the same transaction as the message: a confirmation to the
  sender that names the ticket but never repeats the text (otherwise the shop would deliver anybody's text to any
  address), and the whole message to the owner (`STORE_CONTACT_EMAIL`, else the admin's address).
- **Spam without a CAPTCHA:** a honeypot field hidden from people and screen readers (filled in: the request looks
  accepted, nothing is saved or sent); at most 3 messages per address per hour (429, Retry-After 3600) and 200 for the
  whole shop. A CAPTCHA would add a third party that sees every visitor and makes the form harder to use.
- **Admin -> Messages:** newest first, filtered by NEW/ANSWERED/CLOSED; one answer per message, e-mailed with the
  customer's message quoted below it, and who answered and when is stored; then the message is closed. Follow-ups
  continue by e-mail.
- **Privacy:** the privacy policy lists what the form keeps and that it is used only to answer.

## Consequences
- No new service and no mailbox needed to start: until the domain's mail is set up, messages are in the admin page and
  in the outbox (Mailpit locally).
- A per-address limit does not stop a bot that uses a new address each time; the shop-wide limit and the honeypot do
  most of the work at this size. If spam gets through anyway, the next step is a limit per client IP (needs the real
  IP from the proxy headers) or a privacy-friendly challenge.
- One answer per message keeps the record simple; a longer conversation happens in the owner's mail program.
- Messages are kept like order records; deleting them on request is a manual database task for now.
- Proven by `ContactServiceTest`, `ContactIntegrationTest`, the access-control matrix and
  `playwright/tests/contact.spec.ts` (REQ-CS-01..04).
