# ADR 0004: Short access tokens plus rotating, opaque refresh tokens with reuse detection

- **Status:** Accepted
- **Date:** 2026-10 (PR #19)

## Context
The first version issued one JWT valid for hours and had no real logout. A stolen token worked until it expired,
and "Log out" only forgot it in the browser.

## Decision
- **Access token:** a JWT valid for 15 minutes (HS256, issuer checked). It is not stored on the server.
- **Refresh token:** 256 random bits valid for 14 days. Only its SHA-256 hash is stored.
  - Every use **rotates** it: the old token is spent and a successor in the same family is issued.
  - **Reuse** of a spent token (theft or replay) revokes the whole family.
  - **Grace period of 30 s:** a spent token works once more while its successor is unused and the family is
    alive. A lost response or a page reload during a refresh is not treated as theft (found with Edge, see the
    module guide).
- **Logout** revokes the family on the server.
- **Login lockout:** 5 failures in 15 minutes lock the e-mail for 15 minutes. The counters live in Redis
  (shared by every instance), with an in-memory fallback.

## Alternatives considered
- **HttpOnly cookie sessions:** no token in JavaScript, but they bring CSRF handling, and the API clients (k6,
  REST Assured, Playwright) would need cookie logic. This is listed as a known limitation.
- **Access-token denylist:** a database lookup on every request, to shorten a window of at most 15 minutes. Not
  worth it.

## Consequences
- A logged-out access token stays valid for up to 15 minutes. This is documented.
- The frontend refreshes silently and retries once (`api.js` interceptor).
