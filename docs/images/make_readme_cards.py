#!/usr/bin/env python3
"""
Draws the two README cards: the Project Status Board and the GitHub Stats card (plain SVG, no dependencies).

The numbers are copied from the CI run of the commit named below (check-run annotations) and from the GitHub
API; update them and run `python3 docs/images/make_readme_cards.py` after a release.
"""
from pathlib import Path

OUT = Path(__file__).resolve().parent

# ---- from the GitHub Actions run of PR #18 (commit 32a9e29, 29 Oct 2026) ----
AS_OF = "PR #18 @ 32a9e29 · 29 Oct 2026"
LEVELS = [  # (label, tests, colour)
    ("Backend unit + integration", 1072, "#4fb3a9"),
    ("API · DB · BDD · Selenium UI", 459, "#f4b04a"),
    ("Playwright (5 browsers/devices)", 372, "#8fb8de"),
    ("Appium phones", 8, "#e58a8a"),
]
SKIPPED = 3
GATES = ["Backend tests", "API · DB · BDD · UI", "Coverage 97.2 %", "Mutation 87.6 %", "Contract: consumer",
         "Contract: provider", "Accessibility", "Firefox + Edge", "Performance (k6)", "OWASP ZAP",
         "Dependencies (OSV)", "Docker smoke", "Traceability", "Jenkinsfile lint", "Public demo stack",
         "Playwright", "Android (Appium)", "iOS (Appium)", "Production stack"]

# ---- from the GitHub API and git (same day) ----
# The git history of the code is kept from the E-Commerce Suite (4 to 24 Oct); the store repository starts on 25 Oct.
COMMITS_PER_DAY = [("04", 31), ("05", 5), ("06", 2), ("07", 23), ("08", 7), ("09", 2), ("10", 5), ("11", 18),
                   ("12", 11), ("13", 10), ("14", 19), ("15", 3), ("16", 27), ("17", 20), ("18", 29), ("19", 1),
                   ("20", 3), ("23", 3), ("24", 1), ("25", 16), ("26", 3)]
MERGED_PRS, OPEN_PRS, CI_RUNS, LOC = 11, 2, 29, "51k"
LANGUAGES = [("Java", 73.9, "#b07219"), ("JavaScript", 16.0, "#f1e05a"), ("TypeScript", 4.0, "#3178c6"),
             ("CSS", 2.7, "#663399"), ("Other", 3.4, "#b4add8")]  # Gherkin, Shell, Python, Makefile, HTML, Docker
# the road to launch (docs/store/ROADMAP.md): (phase, short name, state) - state: done | now | next | later
PHASES = [("0", "Products and brand", "now"), ("1", "Foundation", "done"), ("2", "Safe card payments", "done"),
          ("3", "Tax, shipping, e-mail", "done"), ("4", "Business and legal", "now"), ("5", "Production server", "now"),
          ("6", "UAT and launch", "later")]

# the store's colours: indigo frame, sunflower titles
BG, PANEL, INK, MUTED, OK = "#262063", "#352e86", "#efecff", "#b9b3e0", "#4ade80"
TITLE = "#ffd35c"
FONT = "font-family=\"'Segoe UI', Helvetica, Arial, sans-serif\""


def text(x, y, s, size=13, fill=INK, weight=400, anchor="start"):
    return f'<text x="{x}" y="{y}" {FONT} font-size="{size}" font-weight="{weight}" fill="{fill}" text-anchor="{anchor}">{s}</text>'


def tile(x, y, w, big, label, sub):
    return (f'<rect x="{x}" y="{y}" width="{w}" height="96" rx="10" fill="{PANEL}"/>'
            + text(x + 16, y + 40, big, 28, "#ffffff", 700) + text(x + 16, y + 62, label, 13, INK, 600)
            + text(x + 16, y + 81, sub, 11.5, MUTED))


def status_board():
    total = sum(n for _, n, _ in LEVELS)
    w, h = 1000, 740
    p = [f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" xmlns="http://www.w3.org/2000/svg" role="img" '
         f'aria-label="Project status board: {total} automated tests, {total - SKIPPED} passed and {SKIPPED} skipped by design; '
         f'19 of 19 CI gates passed; 97.2 percent line coverage; 87.6 percent mutation score; 160 of 160 requirements traced; '
         f'road to launch: phase 1 foundation done, phase 0 products in progress, phases 2 and 3 (safe card payments; tax, shipping and e-mail) done, phase 4 business and legal in progress, phase 5 production server built.">',
         f'<rect width="{w}" height="{h}" rx="14" fill="{BG}"/>',
         text(32, 48, "Project Status Board", 24, TITLE, 700),
         text(32, 72, f"RbcTcsWorld Shop · {AS_OF} · every gate green", 13, MUTED)]
    tiles = [(f"{total:,}", "automated tests", f"{total - SKIPPED:,} passed · {SKIPPED} skipped"),
             ("19 / 19", "CI gates passed", "GitHub Actions + Jenkins"),
             ("97.2 %", "line coverage", "93.1 % branches (JaCoCo)"),
             ("87.6 %", "mutation score", "test strength 93.7 % (PIT)"),
             ("160 / 160", "requirements traced", "checked on every push")]
    tw = (w - 64 - 4 * 12) / 5
    for i, (big, label, sub) in enumerate(tiles):
        p.append(tile(32 + i * (tw + 12), 92, tw, big, label, sub))

    # tests by level: one stacked bar
    p.append(text(32, 228, "Tests by level", 15, INK, 700))
    x, bw = 32.0, w - 64
    for label, n, col in LEVELS:
        seg = bw * n / total
        p.append(f'<rect x="{x:.1f}" y="242" width="{max(seg - 2, 2):.1f}" height="26" rx="4" fill="{col}"/>')
        x += seg
    lx = 32
    for label, n, col in LEVELS:
        p.append(f'<rect x="{lx}" y="282" width="12" height="12" rx="3" fill="{col}"/>')
        s = f"{label} {n}"
        p.append(text(lx + 18, 293, s, 12.5, INK))
        lx += 18 + len(s) * 6.7 + 26

    # gate grid
    p.append(text(32, 336, "Quality gates (a red gate fails the build)", 15, INK, 700))
    cols, gw, gh = 3, (w - 64 - 2 * 12) / 3, 30
    for i, g in enumerate(GATES):
        cx, cy = 32 + (i % cols) * (gw + 12), 350 + (i // cols) * (gh + 6)
        p.append(f'<rect x="{cx:.1f}" y="{cy}" width="{gw:.1f}" height="{gh}" rx="8" fill="{PANEL}"/>')
        p.append(f'<circle cx="{cx + 17:.1f}" cy="{cy + 15}" r="8" fill="{OK}"/>'
                 f'<path d="M{cx + 13:.1f} {cy + 15} l3 3 l5 -6" fill="none" stroke="{BG}" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>')
        p.append(text(round(cx + 34, 1), cy + 20, f"{i + 1:>2}  {g}".strip(), 12.5, INK))

    # road to launch
    p.append(text(32, 640, "Road to launch (docs/store/ROADMAP.md)", 15, INK, 700))
    fills = {"done": OK, "now": TITLE, "next": "#8fb8de", "later": PANEL}
    words = {"done": "done", "now": "in progress", "next": "next", "later": "planned"}
    pw = (w - 64 - 6 * 8) / 7
    for i, (num, name, state) in enumerate(PHASES):
        px = 32 + i * (pw + 8)
        dark = state != "later"
        p.append(f'<rect x="{px:.1f}" y="652" width="{pw:.1f}" height="60" rx="10" fill="{fills[state]}"/>')
        p.append(text(round(px + 10, 1), 674, f"Phase {num} · {words[state]}", 10.5, BG if dark else MUTED, 700))
        p.append(text(round(px + 10, 1), 696, name, 11.5, BG if dark else INK, 600))
    p.append("</svg>")
    (OUT / "project-status.svg").write_text("\n".join(p) + "\n", encoding="utf-8")


def github_stats():
    w, h = 1000, 380
    commits = sum(n for _, n in COMMITS_PER_DAY)
    p = [f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" xmlns="http://www.w3.org/2000/svg" role="img" '
         f'aria-label="GitHub stats: {commits} commits on {len(COMMITS_PER_DAY)} active days since 4 October 2026, {MERGED_PRS} merged pull requests, '
         f'{OPEN_PRS} open, {CI_RUNS} CI workflow runs in this repository, about {LOC} lines of code; Java 74 percent, JavaScript 16 percent, TypeScript 4 percent.">',
         f'<rect width="{w}" height="{h}" rx="14" fill="{BG}"/>',
         text(32, 48, "GitHub Stats", 24, TITLE, 700),
         text(32, 72, f"rbchy/RbcTcsWorld · 4 to 26 Oct 2026 · git history kept from the E-Commerce Suite; this repository since 25 Oct", 13, MUTED)]
    nums = [(f"{commits}", "commits"), (f"{MERGED_PRS}", "merged pull requests"), (f"{OPEN_PRS}", "open pull requests"),
            (f"{CI_RUNS}", "CI runs (this repo)"), (f"{len(COMMITS_PER_DAY)}", "active days"), (f"~{LOC}", "lines of code")]
    for i, (big, label) in enumerate(nums):
        x, y = 32 + (i % 2) * 150, 96 + (i // 2) * 70
        p.append(f'<rect x="{x}" y="{y}" width="138" height="60" rx="10" fill="{PANEL}"/>')
        p.append(text(x + 14, y + 30, big, 22, "#ffffff", 700) + text(x + 14, y + 49, label, 11.5, MUTED))

    # commits per day
    cx0, cy0, cw, ch = 360, 122, 608, 158
    p.append(text(cx0, 96, "Commits per day (October 2026)", 15, INK, 700))
    top = max(n for _, n in COMMITS_PER_DAY)
    step = cw / len(COMMITS_PER_DAY)
    p.append(f'<line x1="{cx0}" y1="{cy0 + ch}" x2="{cx0 + cw}" y2="{cy0 + ch}" stroke="{MUTED}" stroke-opacity=".4"/>')
    for i, (day, n) in enumerate(COMMITS_PER_DAY):
        bh = ch * n / top
        bx = cx0 + i * step + 4
        p.append(f'<rect x="{bx:.1f}" y="{cy0 + ch - bh:.1f}" width="{step - 8:.1f}" height="{max(bh, 1.5):.1f}" rx="3" fill="#8b7cf0"/>')
        p.append(text(round(bx + (step - 8) / 2, 1), round(cy0 + ch - bh - 5, 1), str(n), 10.5, INK, 600, "middle"))
        p.append(text(round(bx + (step - 8) / 2, 1), cy0 + ch + 16, day, 10.5, MUTED, 400, "middle"))

    # languages
    p.append(text(32, 326, "Languages", 15, INK, 700))
    x, bw = 32.0, w - 64
    for name, pct, col in LANGUAGES:
        seg = bw * pct / 100
        p.append(f'<rect x="{x:.1f}" y="336" width="{max(seg - 2, 2):.1f}" height="12" rx="3" fill="{col}"/>')
        x += seg
    lx = 140
    for name, pct, col in LANGUAGES:
        p.append(f'<circle cx="{lx}" cy="321" r="5" fill="{col}"/>')
        s = f"{name} {pct} %"
        p.append(text(lx + 10, 325, s, 12, INK))
        lx += 10 + len(s) * 6.6 + 22
    p.append("</svg>")
    (OUT / "github-stats.svg").write_text("\n".join(p) + "\n", encoding="utf-8")


if __name__ == "__main__":
    status_board()
    github_stats()
    print("wrote", OUT / "project-status.svg", "and", OUT / "github-stats.svg")
