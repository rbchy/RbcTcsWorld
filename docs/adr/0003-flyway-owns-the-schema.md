# ADR 0003: Flyway owns the database schema, Hibernate only validates

- **Status:** Accepted
- **Date:** 2026-10 (Module 0, first commit)

## Context
Tests run on H2 (PostgreSQL mode) for speed and on real PostgreSQL in CI, Docker and the demo. The schema has
to be identical everywhere, and every change has to be reviewable and repeatable on an existing database
(the Jenkins Mac, the live demo).

## Decision
- Every schema change is a numbered SQL file in `backend/src/main/resources/db/migration` (V1 ... V11).
- Hibernate runs with `ddl-auto: validate`: it checks that the entities match the tables and never changes them.
- Seed data needed by the application (8 products, coupons, product pictures) is part of the migrations.

## Alternatives considered
- **`ddl-auto: update`:** convenient, but silent, never drops anything, differs between H2 and PostgreSQL, and
  cannot be reviewed.
- **Liquibase:** equally good. Plain SQL was preferred because it is what a DBA reviews.

## Consequences
- The same migrations run in unit-test H2, CI PostgreSQL, Docker, the demo, and the demo's nightly reset (drop
  schema, Flyway rebuilds it).
- A migration is never edited after merge. A fix is a new version.
