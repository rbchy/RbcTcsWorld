# UAT Summary - Release 1.5

> **Status: DRAFT - no session held yet.** Every cell marked `-` is filled in from the facilitator's notes after the
> sessions. Nothing below is a result until then; the decision in section 6 stays **PENDING** until all sessions are done.

| | |
|---|---|
| Plan | [UAT Plan](UAT_PLAN.md) (tasks U1-U10, criteria in section 8) |
| Build under test | `main` @ `-` (commit), CI run `-` (all 18 gates green: yes / no) |
| Environment | public demo / facilitator's Mac (`docker compose --profile app`) - delete one |
| Session dates | - |
| Facilitator | RB Chowdhury |

## 1. Participants

| ID | Profile | Device and browser | Language | Date | Duration |
|---|---|---|---|---|---|
| P1 | - | - | - | - | - |
| P2 | - | - | - | - | - |
| P3 | - | - | - | - | - |
| P4 | - | - | - | - | - |

## 2. Results per task

Legend: ✅ done · 🟡 done with help (one hint) · ❌ not done · (n) = ease 1-5

| Task | Goal | P1 | P2 | P3 | P4 | Done without help | Avg. ease | Requirements |
|---|---|---|---|---|---|---|---|---|
| U1 | Find a product under $50, in stock | - | - | - | - | - / - | - | REQ-UI-04, REQ-CAT-11, REQ-CAT-12, REQ-UI-07 |
| U2 | Guest purchase with WELCOME10 | - | - | - | - | - / - | - | REQ-AUTH-15, REQ-ORD-07, REQ-ORD-08, REQ-CPN-01, REQ-PRC-01, REQ-PAY-01 |
| U3 | Card refused, pay with another | - | - | - | - | - / - | - | REQ-PAY-02, REQ-UI-01 |
| U4 | Account, saved address and card, buy | - | - | - | - | - / - | - | REQ-AUTH-01, REQ-UI-06, REQ-ADR-01, REQ-ADR-03, REQ-PAY-08 |
| U5 | Buy a flash deal, notice "also bought" | - | - | - | - | - / - | - | REQ-CAT-14, REQ-CAT-13 |
| U6 | Track the order, save the invoice | - | - | - | - | - / - | - | REQ-UI-03, REQ-ORD-11, REQ-FUL-02 |
| U7 | Cancel an order not yet sent | - | - | - | - | - / - | - | REQ-ORD-04, REQ-NTF-01 |
| U8 | Return a delivered parcel | - | - | - | - | - / - | - | REQ-RET-01, REQ-RET-02, REQ-UI-03 |
| U9 | Review, and save to wishlist | - | - | - | - | - / - | - | REQ-REV-01, REQ-REV-03, REQ-WSH-01, REQ-WSH-02 |
| U10 | Store owner: create and switch off a coupon | - | - | - | - | - / - | - | REQ-CPN-03, REQ-ADM-04, REQ-ADM-07 |

## 3. Where participants got stuck

One line per observation, in the participant's own words where possible.

| # | Task | Participant | What happened (screen, what they looked for) | Quote |
|---|---|---|---|---|
| 1 | - | - | - | - |

## 4. Issue log

| ID | Task | Severity (S1/S2/S3) | Problem | Seen by | Fix or decision | Fixed in (PR) | Retested by |
|---|---|---|---|---|---|---|---|
| UAT-01 | - | - | - | - | - | - | - |

A real software defect from this table is also written up in [Defect Reports](DEFECT_REPORTS.md) with a regression test.

## 5. Exit criteria (from the plan)

| # | Criterion | Target | Result | Met |
|---|---|---|---|---|
| 1 | U1-U9 done without help | each by >= 3 of those who tried | - | - |
| 2 | U10 store owner | done (one hint allowed) | - | - |
| 3 | Open S1 issues | 0 | - | - |
| 4 | S2 issues | fixed, or accepted with a reason | - | - |
| 5 | Average ease, all tasks | >= 3.5 / 5 | - | - |
| 6 | "Would you buy here?" | yes from >= 3 participants | - | - |

## 6. Decision and sign-off

**Decision: PENDING** (GO / NO-GO, written after the last session)

Reason (2-3 sentences: what worked, what had to be fixed, which risk is accepted and why): -

| Role | Name | Decision | Date |
|---|---|---|---|
| UAT facilitator / QA Lead | RB Chowdhury | - | - |
| Business owner (store owner) | RB Chowdhury | - | - |
| Participants agreed their remarks are recorded correctly | P1 - P4 | - | - |
