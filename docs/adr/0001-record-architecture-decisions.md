# ADR 0001: Record architecture decisions

- **Status:** Accepted
- **Date:** 2026-10-14

## Context
The project grew module by module. The reasons behind the big choices were spread over pull
requests, module guides (docs/modules) and commit messages. A reviewer or interviewer asking "why like this?"
had to dig.

## Decision
Each decision that is expensive to reverse gets a short record in `docs/adr/`. The format follows Michael Nygard:
Context, Decision, Consequences, plus the alternatives considered. Records are numbered and never rewritten. A
changed decision gets a new record that supersedes the old one.

## Consequences
- One place answers "why". The module guides keep answering "how".
- The early records (0002-0009) were written after the fact from the PRs and defect reports. Their dates are the
  dates of the decisions, not of the records.
