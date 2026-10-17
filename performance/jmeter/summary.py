#!/usr/bin/env python3
"""
Reads a JMeter results file (CSV .jtl) and prints, per step and in total: requests, error rate,
median, p95 and p99 in ms. Fails (exit 1) when a service level objective is missed:
  - error rate of every step below 1 %
  - p95 of every step below 1000 ms (search below 500 ms: it is the most frequent read)
Writes a Markdown table to $GITHUB_STEP_SUMMARY and a one-line ::notice:: when running in GitHub Actions.
"""
import csv
import math
import os
import sys

SLO_ERROR_RATE = 1.0          # percent
SLO_P95_MS = 1000
SLO_P95_SEARCH_MS = 500


def percentile(values, p):
    """Nearest-rank percentile (the same definition JMeter's dashboard uses)."""
    if not values:
        return 0
    ordered = sorted(values)
    rank = max(1, math.ceil(p / 100 * len(ordered)))
    return ordered[rank - 1]


def main(path):
    steps = {}
    with open(path, newline='') as f:
        for row in csv.DictReader(f):
            s = steps.setdefault(row['label'], {'ms': [], 'errors': 0, 'why': None})
            s['ms'].append(int(row['elapsed']))
            if row['success'].strip().lower() != 'true':
                s['errors'] += 1
                if s['why'] is None:      # the first failure, so a red run says what went wrong
                    s['why'] = f"{row.get('responseCode', '')} {row.get('responseMessage', '')} " \
                               f"{row.get('failureMessage', '')}".strip()[:200]
    if not steps:
        print('no samples in ' + path)
        return 1

    lines = ['| Step | Requests | Errors | Median ms | p95 ms | p99 ms | SLO |', '|---|---:|---:|---:|---:|---:|---|']
    failed = []
    all_ms, all_errors = [], 0
    for label in sorted(steps):
        s = steps[label]
        n = len(s['ms'])
        rate = 100 * s['errors'] / n
        p95 = percentile(s['ms'], 95)
        limit = SLO_P95_SEARCH_MS if 'search' in label else SLO_P95_MS
        ok = rate < SLO_ERROR_RATE and p95 < limit
        if not ok:
            failed.append(label + (f" ({s['why']})" if s['why'] else ''))
        all_ms += s['ms']
        all_errors += s['errors']
        lines.append(f"| {label} | {n} | {rate:.2f} % | {percentile(s['ms'], 50)} | {p95} | {percentile(s['ms'], 99)} | "
                     f"{'pass' if ok else 'FAIL'} (p95 < {limit}, errors < {SLO_ERROR_RATE:g} %) |")
    total_rate = 100 * all_errors / len(all_ms)
    lines.append(f"| **all** | {len(all_ms)} | {total_rate:.2f} % | {percentile(all_ms, 50)} | {percentile(all_ms, 95)} | "
                 f"{percentile(all_ms, 99)} | {'pass' if not failed else 'FAIL'} |")
    table = '\n'.join(lines)
    print(table)

    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as out:
            out.write('### JMeter shop journey\n\n' + table + '\n')
    if os.environ.get('GITHUB_ACTIONS'):
        print(f"::notice title=JMeter::requests {len(all_ms)} | errors {total_rate:.2f} % | "
              f"p95 {percentile(all_ms, 95)} ms | p99 {percentile(all_ms, 99)} ms | "
              f"SLO {'passed' if not failed else 'failed: ' + ', '.join(failed)}")
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else 'results.jtl'))
