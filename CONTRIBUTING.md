# Contributing

Thanks for looking. This is a portfolio project, but it is run like a team project: every change goes through a
pull request and the same 18 CI gates. The rules below are what those gates and reviews expect.

## Getting started

```bash
cp .env.example .env          # optional: every value has a development default
make app                      # the whole shop in Docker -> http://localhost:5173
make help                     # every other command
```
More detail (Eclipse, Jenkins, phones, live demo) is in [docs/HELP_BN.md](docs/HELP_BN.md).

## Branches and commits

- Branch from `main`: `feature/<short-name>`, `fix/<short-name>`, `chore/<short-name>`.
- Commit messages: an imperative summary line of 72 characters or less, a blank line, then **why** (the diff
  already shows what).
- Never commit secrets. `.env` and `deploy/.env` are ignored. Use `.env.example` for new settings.

## What a pull request needs

1. **Tests at the right level.** A business rule gets a unit test in its package. An API change gets an
   integration test (and a JSON Schema change in `automation/src/test/resources/contracts`). A UI change gets a
   page-object test or a Playwright spec. A bug fix gets a regression test that fails without the fix.
2. **Traceability.** New or changed requirements get a row in
   [docs/qa/TRACEABILITY_MATRIX.md](docs/qa/TRACEABILITY_MATRIX.md). Run `make rtm`.
3. **Database changes** only as a new Flyway file `V<next>__<what>.sql`. Never edit a merged migration
   ([ADR 0003](docs/adr/0003-flyway-owns-the-schema.md)).
4. **UI hooks.** Every element a test touches gets a `data-testid`. Texts go through `frontend/src/i18n.js` in all
   six languages ([ADR 0007](docs/adr/0007-test-hooks-and-one-driver-factory.md)).
5. **Expensive-to-reverse decisions** get an ADR in [docs/adr/](docs/adr/).
6. **Defects** found while working are written up in [docs/qa/DEFECT_REPORTS.md](docs/qa/DEFECT_REPORTS.md):
   evidence, root cause, fix, regression test.

## Quality gates (all must be green before merge)

Backend tests with the coverage gate (lines >= 97 %, branches >= 88 %), mutation score >= 84 %, API contract
(consumer and provider side), API / DB / BDD / Selenium UI, cross-browser, Playwright, Android and iOS phone
tests, accessibility (0 serious / critical), k6 SLOs, OWASP ZAP and dependency scan, the Docker stack, the
public demo stack, the traceability check and the Jenkinsfile lint. The table with the current numbers is in the
[README](README.md#quality-gates).

A test that passes only on a rerun is reported as **flaky**. Flaky tests are fixed, not ignored.
