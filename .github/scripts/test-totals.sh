#!/usr/bin/env bash
# Usage: test-totals.sh <label> <surefire dir> - prints run/failed/errors/skipped/flaky as an annotation.
# A flaky test (failed, then passed on a surefire rerun) gets its own warning annotation: it is visible, not hidden.
python3 - "$1" "$2" <<'PY'
import glob, os, sys, xml.etree.ElementTree as ET
label, d = sys.argv[1], sys.argv[2]
t = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
flakes = 0
classes = 0
for f in glob.glob(d + "/TEST-*.xml"):
    r = ET.parse(f).getroot(); classes += 1
    for k in t: t[k] += int(r.get(k, 0))
    # with surefire reruns the "tests" attribute can be lower than the number of tests in the file
    t["tests"] += max(0, len(list(r.iter("testcase"))) - int(r.get("tests", 0)))
    for tc in r.iter("testcase"):
        if os.environ.get("LIST_TESTS"):    # small suites (phones): one line per test
            state = "FAILED" if tc.find("failure") is not None or tc.find("error") is not None else \
                    "skipped" if tc.find("skipped") is not None else "passed"
            print(f"::notice title={label} test::{tc.get('name')} - {state} in {tc.get('time')} s")
        flaky = tc.findall("flakyFailure") + tc.findall("flakyError")
        if flaky and tc.find("failure") is None and tc.find("error") is None:
            flakes += 1
            msg = " ".join((flaky[0].get("message") or flaky[0].get("type") or "").split())[:400].replace("%", "%25")
            print(f"::warning title=FLAKY {label}: {tc.get('classname', '').split('.')[-1]}.{tc.get('name')}::"
                  f"failed {len(flaky)}x, passed on rerun - {msg}")
print(f"::notice title=Test totals {label}::{t['tests']} tests in {classes} classes | failures {t['failures']} | "
      f"errors {t['errors']} | skipped {t['skipped']} | flaky {flakes}")
PY
