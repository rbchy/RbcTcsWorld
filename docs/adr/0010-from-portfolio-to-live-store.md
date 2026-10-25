# ADR-0010: Build the live store on the portfolio code, in its own repository

- Status: accepted
- Date: 2026-10-25
- Decider: RB Chowdhury (owner)

## Context

The RbcTcsWorld E-Commerce Suite was built as a QA portfolio: the shop is the test object, and it runs in demo mode
with test cards only. The owner now wants a store that sells for real, under the RbcTcsWorld name.

Options considered:

1. **A hosted platform (Shopify).** Fastest and least responsibility: payments, tax, hosting and security are the
   platform's job. About $39 a month (Basic, monthly billing) plus card fees. None of the existing code is used.
2. **Make the portfolio repository the live store.** Keeps one codebase, but every production change (live keys,
   real policies, launch dates) would land in the project recruiters review, and its demo safeguards would have to go.
3. **A new repository that starts from the portfolio code.** The store gets its own history from here on; the
   portfolio stays a stable, finished showcase.

## Decision

Option 3. `rbchy/RbcTcsWorld` starts from E-Commerce Suite `main` at `c5c5091` with the full git history, so every
inherited test, defect report and decision record keeps its context. The quality rules come along unchanged: pull
requests only, all CI gates green, new behaviour with tests and a traced requirement.

The first production change is card handling (phase 2): Stripe Elements, so card numbers never reach our server.

## Consequences

- The portfolio repository is no longer extended with store work; it keeps its own README, numbers and reports.
- This repository must close the gaps listed in [docs/store/ROADMAP.md](../store/ROADMAP.md) before taking real orders:
  payments, tax, shipping, e-mail, production hosting, legal pages and a registered business.
- Running the store means the owner carries what a platform would otherwise carry: PCI scope (kept small by Stripe
  Elements), uptime, backups, tax filing and customer service.
- If the cost of that responsibility turns out too high, option 1 remains open; the product data and the UAT plan carry
  over to any platform.
