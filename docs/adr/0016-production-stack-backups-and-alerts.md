# ADR 0016: The live store on its own stack, with encrypted off-site backups, a weekly restore drill and alerts

- **Status:** Accepted
- **Date:** 2026-10 (store Phase 5)

## Context
The public demo (ADR-0009) is built to be thrown away every night: demo mode, Mailpit, a nightly reset, no backups.
A live store is the opposite: its data is the business (orders, payments, tax records), it must survive a dead disk
or a deleted server, and the owner must hear about a problem before customers do.

## Decision
- **A separate production stack** (`deploy/production/docker-compose.yml`, project `rbctcsworld-prod`), on its own
  server (demo and store both need ports 80/443): Spring profile `prod` (live Stripe keys are only accepted there),
  `DEMO_MODE=false`, no ribbon, no Mailpit, no reset. Every setting the store cannot run without is required
  (`${VAR:?}`): a forgotten one stops the start instead of going live half-configured.
- **Backups (daily, `backup.sh`):** `pg_dump` in custom format, checked readable with `pg_restore --list`,
  encrypted with AES-256 (`BACKUP_PASSPHRASE`, kept outside the server too), a manifest (SHA-256, schema version, row
  counts of tables that only grow), copied to any S3-compatible bucket off the server through the official AWS CLI
  image. 14 days local; the bucket keeps its own history with a lifecycle rule. Every update (`deploy-prod.sh`)
  takes a backup first.
- **Restore drill (weekly, `restore-drill.sh`):** the newest backup is restored into a throw-away PostgreSQL container
  and checked against its manifest; `--from-offsite` does the same from the bucket, as after losing the server.
  `restore.sh` puts a backup back into the live store (asks first; Flyway lifts an older backup to the current schema).
- **Health check (every 5 minutes, `healthcheck.sh`):** storefront, API, every container's health, disk, backup age,
  certificate. It alerts (`alert.sh`: ntfy, Slack or Discord webhook) only when the state changes, so an outage is
  one message and a recovery is another. A server that is off cannot report itself, so the guide adds a free
  outside uptime check.
- **Proven in CI** on every push (job "Production stack"): the stack starts with the live settings; only 80/443
  are open; a customer's order is backed up, encrypted and found in a MinIO bucket; a damaged backup is refused by
  the drill with an alert; the off-site copy restores; `restore.sh` removes an account created after the backup and
  keeps the customer; the health check alerts once for a stopped container and reports the recovery.

## Consequences
- Plain shell scripts and cron, no new service to run or pay for; everything is readable in a few minutes.
- The recovery point is up to a day (daily backup) unless the owner runs `backup.sh` more often; continuous WAL
  archiving is the next step if the store grows.
- The passphrase is the key to every backup: lost passphrase, lost backups. The guide says to keep it in a password
  manager on the first day.
