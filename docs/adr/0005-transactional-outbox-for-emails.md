# ADR 0005: Order e-mails through a transactional outbox

- **Status:** Accepted
- **Date:** 2026-10 (PR #20)

## Context
Every order step (placed, paid, shipped, delivered, return decided) should send the customer an e-mail. Two ways
are wrong:
- sending inside the order transaction (a slow or failing mail server blocks or rolls back the order);
- sending after the commit without a record (a crash between commit and send loses the e-mail).

## Decision
- The order code publishes an `OrderNotificationEvent`. A listener with MANDATORY propagation writes a
  `notifications` row **in the same transaction** as the order change.
- A scheduler claims due rows with a conditional UPDATE (so two backends never send the same row) and sends them
  through SMTP or the log channel.
- Failed sends are retried after 30 s, 1 min, 2 min and 4 min, then marked FAILED after 5 attempts. An admin can
  "Send again" from the E-mails page.
- In development and CI, Mailpit catches everything. The automation suite reads its API to prove "exactly one
  e-mail per event".

## Consequences
- Checkout never waits for the mail server, and no e-mail is lost or duplicated.
- E-mails arrive a few seconds late (poll interval 2 s). Tests wait for them instead of sleeping.
