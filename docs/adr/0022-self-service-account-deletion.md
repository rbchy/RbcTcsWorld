# ADR 0022: Customers delete their own account; orders stay for the tax records

- **Status:** Accepted
- **Date:** 2026-11 (before launch)

## Context
The privacy policy promised to delete an account "on request by e-mail" (ADR-0015, "not done yet"). Privacy laws in
several US states (California's CCPA/CPRA among them) give customers a right to deletion, and a button is easier for
the customer and the owner than an e-mail thread. But a shop must keep its invoices, payments and refunds for years
for tax and accounting law, and those rows point at the user.

## Decision
- **Security -> Delete my account**: the current password and the typed word DELETE; `POST /api/account/delete`
  (204). A POST, because some proxies drop the body of a DELETE.
- **Refused** while something still needs the customer (409): an order placed, paid, shipped or with a return asked
  for, or a return not yet decided. Refused for the wrong password, a guest checkout and staff accounts (400).
- **One transaction removes** the cart, wishlist, address book, saved cards, reviews (each product's rating is
  recalculated), sessions, password-reset links, sign-in codes, contact messages and e-mails still waiting.
- **The user row stays** for the orders: e-mail replaced by `deleted-<id>@deleted.invalid` (the `.invalid` domain can
  never receive mail), a random password nobody knows, two-factor off, inactive, `deleted_at` set (V34). The old
  address is replaced in the e-mail log, may register again, and gets one last "your account was deleted" e-mail.
- **Kept**, as the policy says: orders with their delivery address (the invoice), payments, refunds, returns, coupon
  use, and the suppression list (an address that bounced is still never e-mailed).
- **Audit trail:** `CUSTOMER_DELETED_ACCOUNT` with no e-mail in it; the hash-chained log is never edited.

## Consequences
- The policy text now points to the button; the 45-day e-mail route stays for a copy of one's data or a correction.
- An access token issued before the deletion stops working at once (its e-mail no longer exists).
- Saved cards are removed here; with Stripe the card stays on Stripe's customer record until Stripe's own retention
  ends (the shop holds only the token, which is gone).
- Proven by `AccountDeletionIntegrationTest`, `ReviewServiceTest`, `NotificationServiceTest`, the access-control
  matrix and `playwright/tests/account-deletion.spec.ts` (REQ-AUTH-17).
