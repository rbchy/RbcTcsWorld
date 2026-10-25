# User Acceptance Test (UAT) Plan - Release 1.5

| | |
|---|---|
| Product | RbcTcsWorld E-Commerce Suite (storefront and store back office) |
| Build under test | `main` as deployed on the test environment on the session day (write the commit in the [UAT Summary](UAT_SUMMARY.md)) |
| Prepared by | RB Chowdhury, QA Lead and UAT facilitator |
| Status | **Ready to run** - no session held yet |
| Related | [Test Plan](TEST_PLAN.md) · [Traceability Matrix](TRACEABILITY_MATRIX.md) · [UAT task cards for participants](UAT_TASK_CARDS.md) · [UAT Summary](UAT_SUMMARY.md) |

## 1. Purpose

Unit, integration and system tests (1,489 automated tests, 18 CI gates) prove the shop **meets its written
requirements**. UAT answers the last question, the one no automated test can: **can ordinary people use it for what a
shop is for, without help?** The participants are not testers. They get a goal ("buy a mug as a guest"), never
instructions on which button to press, and the facilitator watches where they hesitate, get lost or give up.

Out of scope: finding small bugs, measuring speed, security and accessibility audits (CI covers these).
A bug found during a session is still logged and fixed, but the session is about fitness for purpose.

## 2. Roles

| Role | Who | Does |
|---|---|---|
| Facilitator | RB Chowdhury | Prepares the environment and data, reads each task aloud, observes and takes notes, never helps unless a participant is fully stuck |
| Participants | 3-4 people (family or friends), see section 4 | Do the tasks while thinking aloud; rate each task |
| Business owner (sign-off) | RB Chowdhury, acting as store owner | Reads the findings and decides GO / NO-GO in the summary |

## 3. Environment and test data

| Item | Value |
|---|---|
| Where | The public demo (HTTPS, `DEMO_MODE=true`, see [ORACLE_CLOUD_BN](../deploy/ORACLE_CLOUD_BN.md)). If the demo is not online yet: the whole app on the facilitator's Mac, `docker compose --profile app up -d --build`, storefront http://localhost:5173 |
| Devices | At least one session on a laptop and one on a phone browser |
| Payment | Test cards only: `4242 4242 4242 4242` (approved), `4000 0000 0000 0002` (declined), any future expiry, any 3-digit CVV. **Participants never type a real card number.** |
| Personal data | Participants use made-up names and the sample address on their task card, never their own address, phone or e-mail |
| E-mails | Shop e-mails land in Mailpit (http://localhost:8025, or on the demo through the owner's SSH tunnel); the facilitator shows them when a task needs one |
| Store owner login | The demo's admin account (facilitator only; the password is never written in this repository) |

**Before each session the facilitator:**

1. Resets the data (`make app` with a fresh database, or waits for the demo's nightly reset).
2. Checks that the coupon `WELCOME10` is active and creates one flash deal on a product (Admin -> Products -> edit -> deal price and times).
   For the "also bought" row in U5, places two orders as a helper account that each contain the deal product together with one other product (the row is built from real orders, and a fresh database has none).
3. For task U8 (return): places one order for the participant's account beforehand, then ships and delivers it as admin, so it can be returned.
4. Opens the storefront in a fresh browser window (no saved logins), in the language the participant prefers (6 languages, Bangla included).
5. Starts a stopwatch per task and has the notes sheet (section 7) ready.

## 4. Participants

| ID | Profile (no names in this repository) | Device | Language |
|---|---|---|---|
| P1 | Adult family member, shops online sometimes | Laptop | English or Bangla |
| P2 | Friend, shops online often on a phone | Phone | English |
| P3 | Family member, rarely shops online | Laptop | Bangla |
| P4 (optional) | Someone who has run a small shop or does office work, for the store-owner tasks | Laptop | English |

Each participant gives verbal consent to be observed and to have their (anonymous) remarks written down.
P1-P3 do tasks U1-U9; the store-owner task U10 goes to P4, or to P1 if there is no fourth person.

## 5. Tasks and the requirements they accept

The participant sees only the **goal** (task card), not the "what passes" column.

| ID | Goal given to the participant | Passes when the participant, without help ... | Requirements accepted |
|---|---|---|---|
| U1 | "Find a product under $50 that is in stock, and look at its details." | uses search or the filter panel and opens a product page | REQ-UI-04, REQ-CAT-11, REQ-CAT-12, REQ-UI-07 |
| U2 | "Buy one item **without creating an account**, send it to the address on your card and use the code WELCOME10." | completes a guest order, sees the discount and an order confirmation | REQ-AUTH-15, REQ-ORD-07, REQ-ORD-08, REQ-CPN-01, REQ-PRC-01, REQ-PAY-01 |
| U3 | "Your first card is refused. Pay with the other card on your task card." | reads the decline message and pays the same order with the second card | REQ-PAY-02, REQ-UI-01 |
| U4 | "Create your own account, save your address and your card, then buy something else." | registers, saves an address and a card, and checks out with them | REQ-AUTH-01, REQ-UI-06, REQ-ADR-01, REQ-ADR-03, REQ-PAY-08 |
| U5 | "Something is on sale right now. Find it and buy it at the sale price. Is there anything people often buy with it?" | finds the deal with its countdown, pays the deal price, notices the "also bought" row | REQ-CAT-14, REQ-CAT-13 |
| U6 | "Where is your order? Find where it is and save the invoice." | opens the order, reads its status and timeline, downloads the invoice PDF | REQ-UI-03, REQ-ORD-11, REQ-FUL-02 |
| U7 | "You changed your mind about an order that has not been sent yet. Cancel it." | cancels the order and sees that it is cancelled (and refunded if paid) | REQ-ORD-04, REQ-NTF-01 |
| U8 | "The parcel arrived but you don't want it. Send it back." | asks for a return with a reason and sees the request | REQ-RET-01, REQ-RET-02, REQ-UI-03 |
| U9 | "Write a review of what you received, and save another product for later." | posts a star rating with text, adds a product to the wishlist and finds it again | REQ-REV-01, REQ-REV-03, REQ-WSH-01, REQ-WSH-02 |
| U10 | Store owner: "Make a coupon that gives 15 % off orders over $40 for this week, then turn it off." | creates, finds and switches off the coupon in the back office | REQ-CPN-03, REQ-ADM-04, REQ-ADM-07 |

Every requirement in this table already has automated tests in the [Traceability Matrix](TRACEABILITY_MATRIX.md);
UAT adds the human verdict on top of them.

## 6. How a session runs (about 45 minutes per participant)

1. **Welcome (3 min):** "We are testing the shop, not you. There are no wrong answers. Please say aloud what you are
   thinking and looking for."
2. **Tasks (35 min):** read one goal aloud, hand over the card, start the stopwatch. The facilitator does not point or
   explain. After 2 minutes stuck, one neutral hint is allowed ("Where would you look for that on another shop?");
   the task then counts as **done with help**.
3. **After each task:** the participant rates it from 1 (very hard) to 5 (very easy) and says in a sentence why.
4. **Close (7 min):** "What was the most confusing moment? Would you buy here? What would you change first?"

## 7. What is recorded

Per participant and task: result (**Done** / **Done with help** / **Not done**), time, ease 1-5,
where they hesitated or got lost (screen and what they looked at), and their own words (short quotes).
Every problem goes into the issue log of the [UAT Summary](UAT_SUMMARY.md) with a severity:

| Severity | Meaning | Example |
|---|---|---|
| S1 - Blocker | The participant could not finish a task the shop exists for | Could not find how to pay |
| S2 - Major | Finished, but only with a hint or after clear confusion | Did not see that the coupon was applied |
| S3 - Minor | Noticed, slowed them a little, or a wish | "I would like a bigger Add to cart button" |

A real software defect found during UAT is also written up in [Defect Reports](DEFECT_REPORTS.md) and gets a regression test.

## 8. Entry and exit criteria

**Entry (sessions may start when):** all 18 CI gates are green on the build under test, no Critical/High defect is
open, the environment is reset and the preparation in section 3 is done.

**Exit (GO) when all of these hold:**

| # | Criterion | Target |
|---|---|---|
| 1 | Tasks U1-U9 done without help | each task by at least 3 of the participants who tried it |
| 2 | Task U10 (store owner) | done, with or without one hint |
| 3 | Open S1 issues | 0 |
| 4 | S2 issues | each one fixed, or accepted by the business owner with a reason |
| 5 | Average ease | at least 3.5 / 5 over all tasks |
| 6 | "Would you buy here?" | yes from at least 3 participants |

**NO-GO** if any S1 stays open, or if two or more criteria miss their target. After fixes, only the failed tasks are
repeated, with at least one new participant.
