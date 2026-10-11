#!/usr/bin/env python3
"""Builds ONE QA report for a pipeline run: qa-reports/index.html with the key numbers of every quality gate,
plus a copy of every detailed report next to it (Allure, Cucumber, JaCoCo, PIT, k6, OWASP ZAP).

Published in Jenkins with the HTML Publisher plugin ("QA Reports" link on the build page).
The dashboard itself uses no JavaScript and no inline CSS, so it renders under Jenkins' default
Content-Security-Policy. Allure and the Cucumber report need JavaScript (see docs/modules/CICD_BN.md).

Usage: python3 ci/qa_dashboard.py [output-dir]   (run from the repository root; missing reports are shown as "not run")
"""
import csv, glob, html, json, os, shutil, sys, time
import xml.etree.ElementTree as ET

OUT = sys.argv[1] if len(sys.argv) > 1 else "qa-reports"
BUILD = os.environ.get("BUILD_NUMBER", "local")
COMMIT = (os.environ.get("GIT_COMMIT") or "")[:7]


def pom_property(name, default):
    try:
        m = __import__("re").search(rf"<{name}>([^<]+)</{name}>", open("backend/pom.xml").read())
        return float(m.group(1)) if m else default
    except OSError:
        return default


GATE_LINE = 100 * pom_property("jacoco.minimum.line", 0.97)
GATE_BRANCH = 100 * pom_property("jacoco.minimum.branch", 0.88)
GATE_MUTATION = pom_property("pitest.mutationThreshold", 0)


def esc(v):
    return html.escape(str(v))


def junit(pattern):
    t = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0, "time": 0.0}
    files = glob.glob(pattern)
    for f in files:
        try:
            r = ET.parse(f).getroot()
        except ET.ParseError:
            continue
        for s in ([r] if r.tag == "testsuite" else r.iter("testsuite")):
            for k in ("tests", "failures", "errors", "skipped"):
                t[k] += int(s.get(k, 0) or 0)
            t["time"] += float(s.get("time", 0) or 0)
    return t if files else None


def jacoco(path):
    if not os.path.exists(path):
        return None
    c = {}
    for r in csv.DictReader(open(path)):
        for k in ("LINE", "BRANCH"):
            for kind in ("COVERED", "MISSED"):
                c[f"{k}_{kind}"] = c.get(f"{k}_{kind}", 0) + int(r[f"{k}_{kind}"])
    pct = lambda k: 100.0 * c[f"{k}_COVERED"] / max(1, c[f"{k}_COVERED"] + c[f"{k}_MISSED"])
    return {"line": pct("LINE"), "branch": pct("BRANCH")}


def pit(path):
    if not os.path.exists(path):
        return None
    st = [m.get("status") for m in ET.parse(path).getroot().iter("mutation")]
    det = sum(s in ("KILLED", "TIMED_OUT", "MEMORY_ERROR", "RUN_ERROR") for s in st)
    cov = len(st) - st.count("NO_COVERAGE")
    return {"total": len(st), "killed": det, "survived": st.count("SURVIVED"), "nocov": st.count("NO_COVERAGE"),
            "score": 100.0 * det / max(1, len(st)), "strength": 100.0 * det / max(1, cov)}


def k6(path):
    if not os.path.exists(path):
        return None
    m = json.load(open(path))["metrics"]
    th = [res.get("ok") for v in m.values() for e, res in (v.get("thresholds") or {}).items() if e != "max>=0"]
    g = lambda k, f: (m.get(k, {}).get("values") or {}).get(f)
    return {"reqs": g("http_reqs", "count"), "failed": g("http_req_failed", "rate"), "p95": g("http_req_duration", "p(95)"),
            "checks": g("checks", "rate"), "vus": g("vus_max", "max"), "th_ok": sum(map(bool, th)), "th": len(th)}


def zap(path):
    if not os.path.exists(path):
        return None
    risks = {"High": 0, "Medium": 0, "Low": 0, "Informational": 0}
    for site in json.load(open(path)).get("site", []):
        for a in site.get("alerts", []):
            risks[a.get("riskdesc", "Informational").split(" ")[0]] += 1
    return risks


def osv(path):
    if not os.path.exists(path):
        return None
    pk = [p for r in json.load(open(path)).get("results", []) for p in r.get("packages", [])]
    return {"packages": len(pk), "advisories": sum(len(p.get("groups", [])) for p in pk)}


def copy(src, dst):
    if os.path.isdir(src):
        shutil.copytree(src, os.path.join(OUT, dst), dirs_exist_ok=True)
        return dst + "/index.html"
    if os.path.isfile(src):
        os.makedirs(os.path.dirname(os.path.join(OUT, dst)) or OUT, exist_ok=True)
        shutil.copy(src, os.path.join(OUT, dst))
        return dst
    return None


os.makedirs(OUT, exist_ok=True)
links = {
    "allure": copy("automation/target/allure-report", "allure"),
    "cucumber": copy("automation/target/cucumber-report.html", "cucumber/cucumber-report.html"),
    "coverage": copy("backend/target/site/jacoco", "coverage"),
    "mutation": copy("backend/target/pit-reports", "mutation"),
    "zap": copy("security/zap/zap-report.html", "security/zap-report.html"),
    "osv": copy("osv-results.json", "security/osv-results.json"),
}
perf = {}
for name in ("smoke", "flash-sale", "catalog", "load", "stress", "spike", "soak"):
    s = k6(f"performance/reports/{name}-summary.json")
    if s:
        perf[name] = (s, copy(f"performance/reports/{name}-report.html", f"performance/{name}-report.html"))

be, au = junit("backend/target/surefire-reports/TEST-*.xml"), junit("automation/target/surefire-reports/TEST-*.xml")
cov, mut = jacoco("backend/target/site/jacoco/jacoco.csv"), pit("backend/target/pit-reports/mutations.xml")
z, o = zap("security/zap/zap-report.json"), osv("osv-results.json")

cards = []  # (title, status, big number, detail lines, [(link text, href)])


def card(title, ok, big, lines, refs=()):
    cards.append((title, "na" if ok is None else ("ok" if ok else "bad"), big, lines, [r for r in refs if r[1]]))


for title, t, ref in (("Backend unit + integration", be, [("JaCoCo coverage", links["coverage"])]),
                      ("Automation: API, DB, BDD, UI", au, [("Allure report", links["allure"]),
                                                            ("Cucumber report", links["cucumber"])]),
                      ("API contract (JSON Schema)", junit("automation/target/surefire-reports/TEST-*ApiContractTest.xml"),
                       [("Allure report", links["allure"])]),
                      ("Accessibility (WCAG 2.1 AA)", junit("automation/target/surefire-reports/TEST-*AccessibilityTest.xml"),
                       [("Allure report", links["allure"])])):
    if t:
        bad = t["failures"] + t["errors"]
        card(title, bad == 0, f"{t['tests'] - bad - t['skipped']}/{t['tests']}",
             [f"passed, {bad} failed, {t['skipped']} skipped", f"{t['time']:.0f} s test time"], ref)
    else:
        card(title, None, "not run", [], ref)

if cov:
    card("Code coverage (JaCoCo)", cov["line"] >= GATE_LINE and cov["branch"] >= GATE_BRANCH, f"{cov['line']:.1f}%",
         [f"lines (gate {GATE_LINE:.0f}%)", f"branches {cov['branch']:.1f}% (gate {GATE_BRANCH:.0f}%)"],
         [("Coverage report", links["coverage"])])
if mut:
    card("Mutation testing (PIT)", mut["score"] >= GATE_MUTATION, f"{mut['score']:.1f}%",
         [f"mutation score (gate {GATE_MUTATION:.0f}%): {mut['killed']}/{mut['total']} mutants killed",
          f"test strength {mut['strength']:.1f}% | {mut['survived']} survived | {mut['nocov']} not covered by unit tests"],
         [("Mutation report", links["mutation"])])
else:
    card("Mutation testing (PIT)", None, "not run", ["enable with RUN_MUTATION"], [])
for name, (s, href) in perf.items():
    ok = s["th_ok"] == s["th"]
    card(f"Performance: k6 {name}", ok, f"{s['p95']:.0f} ms" if s["p95"] is not None else "-",
         [f"p95 | {s['reqs']} requests, {100 * (s['failed'] or 0):.2f}% failed, max {s['vus']} users",
          f"thresholds {s['th_ok']}/{s['th']} passed | checks {100 * (s['checks'] or 0):.1f}%"],
         [(f"k6 {name} report", href)])
if not perf:
    card("Performance (k6)", None, "not run", [], [])
if z:
    card("Security: OWASP ZAP", z["High"] + z["Medium"] == 0, f"{z['High'] + z['Medium']}",
         ["High/Medium alert types", f"Low {z['Low']} | Informational {z['Informational']}"], [("ZAP report", links["zap"])])
else:
    card("Security: OWASP ZAP", None, "not run", [], [])
if o is not None:
    card("Security: dependencies (OSV)", True if o["packages"] == 0 else None, f"{o['packages']}",
         ["vulnerable packages (accepted risks excluded)", f"{o['advisories']} advisories"], [("OSV results (JSON)", links["osv"])])

# The headline must agree with the pipeline (build #30: a build that stopped in the first stage showed
# "all gates passed" from report files of an earlier run). PIPELINE_RESULT = currentBuild.currentResult.
PIPELINE = os.environ.get("PIPELINE_RESULT", "")
if any(c[1] == "bad" for c in cards):
    overall, verdict = "bad", "a gate failed"
elif PIPELINE and PIPELINE != "SUCCESS":
    overall, verdict = "bad", f"pipeline {PIPELINE.lower()} - see Console Output"
elif not any(c[1] == "ok" for c in cards):
    overall, verdict = "bad", "no gate ran"
else:
    overall, verdict = "ok", "all gates passed"
total_tests = sum(t["tests"] for t in (be, au) if t)
parts = []
for title, status, big, lines, refs in cards:
    parts.append(f'<section class="card {status}"><h2>{esc(title)}</h2><p class="big">{esc(big)}</p>'
                 + "".join(f"<p>{esc(l)}</p>" for l in lines)
                 + ("<p class=\"links\">" + " ".join(f'<a href="{esc(h)}">{esc(t)}</a>' for t, h in refs) + "</p>" if refs else "")
                 + "</section>")
page = f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>QA Reports - build {esc(BUILD)}</title><link rel="stylesheet" href="style.css"></head>
<body><header class="{overall}"><h1>RbcTcsWorld E-Commerce - QA Reports</h1>
<p>Build {esc(BUILD)}{(' | commit ' + esc(COMMIT)) if COMMIT else ''} | {esc(time.strftime('%Y-%m-%d %H:%M'))} |
{total_tests} automated tests | overall: <strong>{esc(verdict)}</strong></p></header>
<main>{''.join(parts)}</main>
<footer>Grey card = stage not run in this build. Allure and Cucumber need JavaScript: see docs/modules/CICD_BN.md.</footer>
</body></html>"""
open(os.path.join(OUT, "index.html"), "w").write(page)
open(os.path.join(OUT, "style.css"), "w").write("""
:root{--ok:#1a7f37;--bad:#cf222e;--na:#8c959f;--ink:#1f2328;--muted:#59636e;--bg:#f6f8fa;--card:#fff;--line:#d0d7de}
*{box-sizing:border-box}body{margin:0;font:15px/1.45 -apple-system,BlinkMacSystemFont,"Segoe UI",Helvetica,Arial,sans-serif;color:var(--ink);background:var(--bg)}
header{padding:20px 24px;color:#fff}header.ok{background:var(--ok)}header.bad{background:var(--bad)}
h1{margin:0 0 4px;font-size:22px}header p{margin:0;opacity:.95}
main{display:grid;grid-template-columns:repeat(auto-fill,minmax(270px,1fr));gap:14px;padding:20px 24px}
.card{background:var(--card);border:1px solid var(--line);border-top:5px solid var(--na);border-radius:8px;padding:14px 16px}
.card.ok{border-top-color:var(--ok)}.card.bad{border-top-color:var(--bad)}
h2{margin:0 0 6px;font-size:14px;text-transform:uppercase;letter-spacing:.03em;color:var(--muted)}
.big{font-size:30px;font-weight:700;margin:0 0 4px}.card.bad .big{color:var(--bad)}
.card p{margin:2px 0;color:var(--muted)}.card p.big{color:var(--ink)}
.links{margin-top:10px!important}.links a{display:inline-block;margin:4px 10px 0 0;color:#0969da;font-weight:600;text-decoration:none}
.links a:hover{text-decoration:underline}footer{padding:0 24px 24px;color:var(--muted);font-size:13px}
""")
print(f"QA dashboard: {OUT}/index.html ({len(cards)} gates, overall: {verdict})")
