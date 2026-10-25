# QA documentation

| Document | Answers |
|---|---|
| [Test Strategy](TEST_STRATEGY.md) | How do we test this product? Levels, techniques, tools, environments, data, quality gates |
| [Test Plan](TEST_PLAN.md) | What is tested for release 1.0, in which phases, with which entry and exit criteria? |
| [Risk Register](RISK_REGISTER.md) | What can go wrong, how bad is it, and which tests protect against it? |
| [Traceability Matrix](TRACEABILITY_MATRIX.md) | Which test proves each requirement? (checked in CI by `check_rtm.py`) |
| [Defect Reports](DEFECT_REPORTS.md) | Which real defects were found, how, why they happened and what guards them now |
| [Test Summary Report](TEST_SUMMARY_REPORT.md) | What were the results, and is the release ready (go / no-go)? |
| [UAT Plan](UAT_PLAN.md) · [task cards](UAT_TASK_CARDS.md) | Can ordinary people use the shop for what it is for? 10 goal-based tasks, each tied to its requirements |
| [UAT Summary](UAT_SUMMARY.md) | What did the participants manage, where did they get stuck, what was fixed, and the business owner's GO / NO-GO |

Reading order for a reviewer: Summary Report -> Risk Register -> Traceability Matrix -> Defect Reports.
