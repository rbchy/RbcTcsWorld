# RbcTcsWorld Shop

[![CI](https://github.com/rbchy/RbcTcsWorld/actions/workflows/ci.yml/badge.svg)](https://github.com/rbchy/RbcTcsWorld/actions/workflows/ci.yml)
![Stage](https://img.shields.io/badge/stage-pre--launch%20%C2%B7%20phase%201-f59e0b)
![Java 21](https://img.shields.io/badge/Java-21-blue)
![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-green)

**RbcTcsWorld Shop is an online store on its way to selling for real.** It starts from the
[RbcTcsWorld E-Commerce Suite](https://github.com/rbchy/RbcTcsWorld_ECommerceSuite), a shop I built test-first as a
QA portfolio project, and takes it through the steps a live store needs: safe card payments, real tax, real shipping,
e-mail from its own domain, backups, legal pages and a business behind it.

Nothing here takes real orders yet. Until launch the store runs in demo mode with test cards only.

Owner: **RB Chowdhury** - [GitHub](https://github.com/rbchy) · [LinkedIn](https://www.linkedin.com/in/rbchy/) · [Website](https://rbchy.github.io/rbchy/)

---

## Where it stands

| | |
|---|---|
| Started from | E-Commerce Suite `main` @ `c5c5091` (25 Oct 2026), full history kept |
| Inherited | catalog, cart, checkout, orders, stock across warehouses, coupons, flash deals, returns, reviews, wishlist, e-mails, accounts with two-factor sign-in, saved-card wallet, store back office, audit trail, six languages |
| Inherited quality bar | 1,489 automated tests, 18 CI quality gates, 97.3 % line coverage, 89.3 % mutation score, 129 traced requirements |
| Current phase | **Phase 1 - foundation** (this repository set up for the store) |
| Not decided yet | what the store sells, where the products come from |

The full description of the inherited system - test architecture, quality gates, pipeline, the defects the tests found -
is in [docs/FOUNDATION.md](docs/FOUNDATION.md). The decision to build the store on this code is recorded in
[ADR-0010](docs/adr/0010-from-portfolio-to-live-store.md).

## Roadmap to launch

The details, owners and running costs are in [docs/store/ROADMAP.md](docs/store/ROADMAP.md).

| Phase | What | Who | Status |
|---|---|---|---|
| 0 | Decide the products, suppliers and brand | Owner | open |
| 1 | This repository: history, links, README, roadmap, decision record | Engineering | **in progress** |
| 2 | Card payments through Stripe Elements and webhooks (card numbers never reach our server) | Engineering | next |
| 3 | Real sales tax, shipping rates and labels, e-mail from the store's domain | Engineering | planned |
| 4 | Business registration, sales tax license, privacy policy, terms, return policy | Owner + professionals | planned |
| 5 | Production server: demo mode off, daily backups, monitoring, restore drill | Engineering | planned |
| 6 | User acceptance test with real cards and refunds, then launch | Owner + Engineering | planned |

Every phase keeps the inherited rule: a change reaches `main` only through a pull request with all CI gates green, and
new behaviour arrives with its tests and a traced requirement.

## Run it

Same commands as the foundation project:

```bash
make doctor     # checks the machine first
make app        # PostgreSQL + Redis + Mailpit + backend + storefront (http://localhost:5173)
make test       # the automated suites
make help       # everything else
```

Test cards: `4242 4242 4242 4242` (approved), `4000 0000 0000 0002` (declined), any future expiry, any CVV.

## License

[MIT](LICENSE)
