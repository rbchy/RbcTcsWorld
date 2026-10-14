# ADR 0009: Public demo on one free server, behind Caddy, in demo mode

- **Status:** Accepted
- **Date:** 2026-10 (PR #22)

## Context
Reviewers should be able to click a link and use the shop. The link must not cost money every month, must not
leak secrets, and must not let visitors put arbitrary content on a public page.

## Decision
- **One Oracle Cloud "Always Free" VM** runs `deploy/docker-compose.yml`.
  - Caddy terminates HTTPS with automatic Let's Encrypt certificates.
  - The address is `<ip>.sslip.io` (no domain needed).
  - Only ports 80/443 are reachable.
- **`DEMO_MODE=true`:**
  - the backend refuses the development JWT secret and admin password;
  - only the shop's own product pictures are accepted;
  - the storefront shows a "nothing here is real" ribbon;
  - payments are mock only.
- A nightly reset rebuilds the schema through Flyway.
- `deploy/smoke.sh` checks the demo from outside after every deploy and daily from GitHub.
- The same stack runs in CI on every push (gate "Public demo stack").

## Alternatives considered
- **Google Cloud Run + Cloud SQL:** managed, but about $10 a month for the database.
- **Render free tier:** the database is deleted after 30 days, and the service sleeps after 15 minutes.

## Consequences
- Free and always on, as long as Oracle's free limits hold (they were reduced in June 2026: 2 OCPU / 12 GB).
- One server, so no high availability. Acceptable for a demo.
