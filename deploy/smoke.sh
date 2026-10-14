#!/usr/bin/env bash
# Checks a running demo from the outside, like a visitor: HTTPS, storefront, API, pictures, security headers,
# demo safeguards. Read-only (no account, no order). Used by deploy.sh and by the "Live demo" GitHub workflow.
#   deploy/smoke.sh https://129-146-10-20.sslip.io
# CURL_OPTS="-k" for a test certificate (CI uses Caddy's local CA).
set -uo pipefail
BASE="${1:?usage: smoke.sh https://<demo address>}"
BASE="${BASE%/}"
CURL="curl -sS --max-time 20 ${CURL_OPTS:-}"
fail=0
check() {   # check "<what>" <command...>
  local what="$1"; shift
  if "$@" >/dev/null 2>&1; then echo "  ok    $what"; else echo "  FAIL  $what"; fail=1; fi
}
headers() { $CURL -o /dev/null -D - "$1"; }

echo "Smoke test of $BASE"
html=$($CURL "$BASE/")
check "storefront answers over HTTPS"           grep -q '<div id="root">' <<<"$html"
js=$(grep -o '/assets/index-[^"]*\.js' <<<"$html" | head -1)
check "storefront is the demo build (ribbon)"  bash -c "$CURL '$BASE$js' | grep -q demo-ribbon"
check "HSTS header"                             grep -qi '^strict-transport-security:' <<<"$(headers "$BASE/")"
check "storefront CSP header"                   grep -qi "^content-security-policy:.*frame-ancestors 'none'" <<<"$(headers "$BASE/")"
if [[ "$BASE" == https://* ]]; then
  http_base="http://${BASE#https://}"
  check "http:// redirects to https://"         grep -qiE '^location: https://' <<<"$(headers "$http_base/")"
fi

products=$($CURL "$BASE/api/products?size=100")
check "catalog API returns the seeded products" grep -q '"sku":"ELEC-MOUSE-001"' <<<"$products"
check "products carry their pictures"          grep -q '"imageUrl":"/products/elec-mouse-001.svg"' <<<"$products"
check "picture file is served as SVG"           grep -qi '^content-type: image/svg+xml' <<<"$(headers "$BASE/products/elec-mouse-001.svg")"
check "fallback picture is served"              grep -qi '^HTTP/[0-9.]* 200' <<<"$(headers "$BASE/products/product.svg")"
check "API sends its strict CSP"                grep -qi "^content-security-policy: default-src 'none'" <<<"$(headers "$BASE/api/products")"

code() { $CURL -o /dev/null -w '%{http_code}' "$@"; }
check "wrong password is 401"                   test "$(code -X POST -H 'Content-Type: application/json' \
      -d '{"email":"nobody@rbctcsworld.test","password":"Wrong-Pass-1"}' "$BASE/api/auth/login")" = 401
check "development admin password is not valid" test "$(code -X POST -H 'Content-Type: application/json' \
      -d '{"email":"admin@rbctcsworld.com","password":"Admin@12345"}' "$BASE/api/auth/login")" != 200
check "admin API needs a login"                 test "$(code "$BASE/api/admin/orders")" = 401
check "API description is not public"          bash -c "! $CURL '$BASE/v3/api-docs' | grep -q '\"openapi\"'"
check "no stack traces on bad input"            bash -c "! $CURL '$BASE/api/products/abc' | grep -qi 'exception'"

if [ "$fail" -ne 0 ]; then echo "Smoke test FAILED"; exit 1; fi
echo "Smoke test passed"
