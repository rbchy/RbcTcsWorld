# ADR 0014: Order e-mail from the store's own domain through Amazon SES

- **Status:** Accepted
- **Date:** 2026-10 (store Phase 3, part 3 of 3)

## Context
Order e-mails went to Mailpit (tests) or the log. A live store must send them from its own domain, land in the
inbox rather than spam, and stop e-mailing addresses that bounce or complain: mailbox providers and SES itself
punish senders that keep doing so, up to suspending the account.

## Decision
- **A third mail channel, `ses`** (`NOTIFICATIONS_CHANNEL`), next to `log` and `smtp`. The transactional outbox,
  retries and redaction stay exactly as they were (ADR-0005); only the last step changes.
- **SES API v2 over HTTPS, signed by hand with AWS Signature V4.** No AWS SDK (one small class instead of a large
  dependency tree) and no SMTP port to open on the server. The signer is checked against signatures computed by
  botocore, the AWS SDK for Python, for permanent and temporary keys.
- **Sender:** `MAIL_FROM` on the store's domain, verified in SES with Easy DKIM, plus SPF through a custom MAIL FROM
  domain and a DMARC record. The domain is a placeholder until the owner buys one.
- **Every non-200 answer fails the attempt**, so throttling, an outage and a rejected message are retried by the
  outbox (30 s, 1, 2, 4 minutes) and then marked FAILED for the admin.
- **Bounces and complaints:** a configuration set publishes them to an SNS topic; SNS posts them to
  `POST /api/notifications/ses/events`.
  - Only messages signed by SNS (SignatureVersion 1 or 2) from the configured topic are accepted. The certificate
    must come from an https `sns.<region>.amazonaws.com` address, or anyone could sign with their own.
  - The subscription is confirmed automatically.
  - A permanent bounce or a complaint puts the address on `email_suppressions`; the outbox never sends to it again
    and marks such e-mails FAILED with the reason. Temporary bounces change nothing.
- Without `SES_SNS_TOPIC_ARN` the endpoint answers 503; nothing is accepted unsigned.

## Consequences
- The store can send from its own domain as soon as the domain is verified and SES production access is granted
  (new SES accounts start in the sandbox: only verified recipients, 200 e-mails a day).
- Not done yet: the admin cannot yet see or lift a suppression (for a customer who fixed a mailbox), and e-mails are
  plain text only.
