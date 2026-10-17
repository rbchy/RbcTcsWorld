# JMeter: shop journey

The same kind of load the k6 tests make, written for Apache JMeter (many QA teams use JMeter):
**register → login → search → add to cart**, a new shopper every loop, 300 ms think time.

| Property | Default | Meaning |
|---|---|---|
| `users` | 50 | parallel shoppers |
| `rampup` | 30 | seconds until all shoppers run |
| `loops` | 5 | journeys per shopper |
| `host` / `port` / `protocol` | localhost / 8081 / http | where the backend runs |

```bash
jmeter -n -t performance/jmeter/shop-journey.jmx -Jusers=100 -l results.jtl -e -o report
python3 performance/jmeter/summary.py results.jtl     # p95 per step + error rate, exit 1 if an SLO is missed
```

SLOs: error rate below 1 % for every step; p95 below 1000 ms (search below 500 ms).
In GitHub: Actions → *Performance (manual)* → test `jmeter`, args e.g. `-Jusers=100`.
The HTML dashboard (`report/`) is uploaded as the `jmeter-report` artifact.

## Results (GitHub runner, 2 CPUs, fresh backend + PostgreSQL, Oct 2026)

| Shoppers | Journeys | Requests | Errors | p95 | p99 | SLO |
|---:|---:|---:|---:|---:|---:|---|
| 50 | 250 | 1000 | 0.00 % | 93 ms | 146 ms | passed |
| 100 | 500 | 2000 | 0.00 % | 77 ms | 130 ms | passed |

Each run is on a fresh runner, so small differences between runs are noise; compare trends, not single numbers.
