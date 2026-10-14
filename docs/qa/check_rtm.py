#!/usr/bin/env python3
"""Fails if the Traceability Matrix references a test that does not exist (living documentation).

Checks every back-ticked reference in docs/qa/TRACEABILITY_MATRIX.md:
  `SomeTest#method`  -> a test class SomeTest.java declares a method named `method`
  `bdd:Scenario name` -> a Cucumber scenario with exactly this name exists
  `performance/...`, `playwright/...`, `deploy/...`, `mobile/...` -> the file exists
Also prints coverage: requirements with and without tests.
"""
import pathlib, re, sys

root = pathlib.Path(__file__).resolve().parents[2]
rtm = (root / "docs/qa/TRACEABILITY_MATRIX.md").read_text(encoding="utf-8")

classes = {p.stem: p.read_text(encoding="utf-8")
           for p in root.glob("**/src/test/java/**/*.java")}
scenarios = set()
for f in root.glob("automation/src/test/resources/features/*.feature"):
    for line in f.read_text(encoding="utf-8").splitlines():
        m = re.match(r"\s*Scenario(?: Outline)?:\s*(.+?)\s*$", line)
        if m:
            scenarios.add(m.group(1))

errors, refs = [], 0
for ref in re.findall(r"`([^`]+)`", rtm):
    if ref.startswith("bdd:"):
        refs += 1
        if ref[4:] not in scenarios:
            errors.append(f"missing scenario: {ref[4:]}")
    elif "#" in ref and re.match(r"^[A-Z]\w*#\w+$", ref):
        refs += 1
        cls, method = ref.split("#")
        src = classes.get(cls)
        if src is None:
            errors.append(f"missing class: {cls}")
        elif not re.search(r"\b(?:void|[\w<>]+)\s+" + re.escape(method) + r"\s*\(", src):
            errors.append(f"missing method: {ref}")
    elif ref.startswith(("performance/", "playwright/", "deploy/", "mobile/")):
        refs += 1
        if not (root / ref).exists():
            errors.append(f"missing file: {ref}")

rows = [l for l in rtm.splitlines() if re.match(r"\|\s*(REQ|NFR)-", l)]
gaps = [l.split("|")[1].strip() for l in rows if "| - |" in l]
print(f"RTM: {len(rows)} requirements, {refs} test references, {len(rows) - len(gaps)} covered, gaps: {', '.join(gaps) or 'none'}")
for e in errors:
    print(f"::error title=Traceability matrix::{e}")
sys.exit(1 if errors else 0)
